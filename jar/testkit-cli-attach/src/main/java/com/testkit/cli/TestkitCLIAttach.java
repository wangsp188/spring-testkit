package com.testkit.cli;


import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TestkitCLIAttach {

    private static final String TARGET_CLASS = "com.testkit.server.TestkitServerManage";


    public static void agentmain(String args, Instrumentation inst) {
        List<String> logs = new ArrayList<>();
        String logPath = null;
        Throwable e = null;
        try {
            logs.add("cli args:" + args);
            Map<String, String> arg = decode(args);
            logPath = arg.get("log-path");
            //帮我解析一些，
            String starterJar = arg.get("starter");
            String ctxGuidingDrug = arg.get("ctx");
            String pidPath = arg.get("pid-path");
            if (starterJar == null || ctxGuidingDrug == null || pidPath == null || pidPath.isEmpty()) {
                throw new IllegalArgumentException("Testkit cli params not enough," + args);
            }

            String envKey = arg.get("env-key");
            envKey = envKey==null || envKey.isEmpty()?"spring.profiles.active":envKey;

            // 解析上下文引导信息
            int hashIndex = ctxGuidingDrug.indexOf('#');
            String ctxClsStr = ctxGuidingDrug.substring(0, hashIndex);
            String ctxFieldStr = ctxGuidingDrug.substring(hashIndex + 1);
            Class appCls = findClassLoadedByApp(inst, "org.springframework.context.ApplicationContext");
            if (appCls == null) {
                throw new IllegalArgumentException("app Class : org.springframework.context.ApplicationContext not found");
            }
            logs.add("appCls is load by " + appCls.getClassLoader().getClass().getName());
            Class<?> ctxCls = null;
            try {
                ctxCls = appCls.getClassLoader().loadClass(ctxClsStr);
            } catch (ClassNotFoundException e2) {
                throw new IllegalArgumentException("not found this ctx class in select jvm : "+ctxClsStr, e2);
            }
            // 获取上下文对象
            Field ctxField = ctxCls.getDeclaredField(ctxFieldStr);
            //判断是否是静态的
            if (!Modifier.isStatic(ctxField.getModifiers())) {
                throw new RuntimeException(ctxFieldStr + " must be statis in "+ctxClsStr);
            }
            ctxField.setAccessible(true);
            Object ctx = ctxField.get(null);
            if (ctx == null) {
                throw new RuntimeException("The value of " + ctxClsStr + "#" + ctxFieldStr + " in the jvm is null");
            }
            Method isActive = ctx.getClass().getMethod("isActive");
            if (!Objects.equals(isActive.invoke(ctx), Boolean.TRUE)) {
                throw new RuntimeException("application ctx is not ready, pls wait and try again");
            }
            // 分层加载目标类
            Class<?> serverManageClass = loadTargetClass(logs, inst, ctxCls, starterJar);
            // 反射调用核心方法
            invokeServerMethod(appCls, serverManageClass, ctx,envKey,pidPath);
        } catch (Throwable t) {
            e = t;
        } finally {
            log(logPath, logs, e);
        }
    }

    // 解码：URL参数字符串 → Map
    public static Map<String, String> decode(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx == -1) {
                // 处理没有值的参数（如 "key"）
                String key = decodeComponent(pair);
                params.put(key, "");
            } else {
                // 分割键值对
                String key = decodeComponent(pair.substring(0, idx));
                String value = decodeComponent(pair.substring(idx + 1));
                params.put(key, value);
            }
        }
        return params;
    }


    // URL组件解码
    private static String decodeComponent(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 decoding not supported", e);
        }
    }

    private static Class<?> loadTargetClass(List<String> logs, Instrumentation inst, Class<?> ctxCls, String jarPath) {
        //1.自己找
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            if (clazz.getName().equals(TARGET_CLASS)) {
                ClassLoader classLoader = clazz.getClassLoader();
                if (URLClassLoader.class.isAssignableFrom(classLoader.getClass())
                        || "org.springframework.boot.loader.launch.LaunchedClassLoader".equals(classLoader.getClass().getName())
                        || "sun.misc.Launcher$AppClassLoader".equals(classLoader.getClass().getName())
                        || "jdk.internal.loader.ClassLoaders$AppClassLoader".equals(classLoader.getClass().getName())
                ) {
                    logs.add("already have TARGET_CLASS");
                    return clazz;
                }
                break;
            }
        }

        // 2.动态添加JAR到类路径加载
        try {
            URL jarUrl = new File(jarPath).toURI().toURL();
            ClassLoader targetLoader = ctxCls.getClassLoader();

            addURLToClassLoader(targetLoader, jarUrl);
            logs.add("add addURLToClassLoader success");
            return Class.forName(TARGET_CLASS, true, targetLoader);
        } catch (Throwable e) {
            logs.add("add addURLToClassLoader fail," + e.getMessage());
            // 阶段3：创建继承ctxCls类加载器的新类加载器
            try {
                URL jarUrl = new File(jarPath).toURI().toURL();
                // 创建继承ctxCls类加载器的子类加载器
                ClassLoader parentLoader = ctxCls.getClassLoader();
                URLClassLoader childLoader = new URLClassLoader(
                        new URL[]{jarUrl},
                        parentLoader
                );
                return childLoader.loadClass(TARGET_CLASS);
            } catch (ClassNotFoundException e1) {
                throw new RuntimeException("Failed to load class. Please check JAR contents: " + TARGET_CLASS);
            } catch (MalformedURLException e1) {
                throw new RuntimeException("Invalid JAR path: " + jarPath);
            }
        }
    }


    private static void addURLToClassLoader(ClassLoader loader, URL jarUrl)
            throws Exception {

        // JDK8及以下版本处理逻辑
        if (loader instanceof URLClassLoader) {
            URLClassLoader urlLoader = (URLClassLoader) loader;
            Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrlMethod.setAccessible(true);
            addUrlMethod.invoke(urlLoader, jarUrl);
            return;
        }

        // JDK9+版本处理逻辑
        try {
            Field ucpField;
            try {
                ucpField = loader.getClass().getDeclaredField("ucp");
            } catch (NoSuchFieldException e) {
                // 处理Spring Boot类加载器等特殊情况
                ucpField = loader.getClass().getSuperclass().getDeclaredField("ucp");
            }
            ucpField.setAccessible(true);
            Object ucp = ucpField.get(loader);
            Method addURLMethod = ucp.getClass().getDeclaredMethod("addURL", URL.class);
            addURLMethod.setAccessible(true);
            addURLMethod.invoke(ucp, jarUrl);
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            throw new UnsupportedOperationException(
                    "Unsupported class loader type: " + loader.getClass().getName(), e);
        }
    }

    private static Class findClassLoadedByApp(Instrumentation inst, String className) {
        // 使用Instrumentation检查所有已加载类
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            ClassLoader classLoader = clazz.getClassLoader();
            if (clazz.getName().equals(className)
                    && ("org.springframework.boot.loader.launch.LaunchedClassLoader".equals(classLoader.getClass().getName())
                    || "org.springframework.boot.loader.LaunchedURLClassLoader".equals(classLoader.getClass().getName())
                    || "sun.misc.Launcher$AppClassLoader".equals(classLoader.getClass().getName())
                    || "jdk.internal.loader.ClassLoaders$AppClassLoader".equals(classLoader.getClass().getName()))
            ) {
                return clazz;
            }
        }
        return null;
    }

    private static void invokeServerMethod(Class appCls, Class<?> clazz, Object ctx, String envKey, String pidPath)
            throws Exception {

        String env = null;
        try {
            // 获取getEnvironment方法（无参数）
            Method getEnvMethod = appCls.getMethod("getEnvironment");

            // 调用getEnvironment方法获取环境对象
            Object environment = getEnvMethod.invoke(ctx);

            // 获取getProperty方法（String参数）
            Method getPropertyMethod = environment.getClass()
                    .getMethod("getProperty", String.class);

            // 调用getProperty方法获取配置值
            env = (String) getPropertyMethod.invoke(environment, envKey);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Spring environment method does not exist, please confirm the Spring version (requires 4.0+)", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Method access permission is abnormal. Check SecurityManager configuration", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new RuntimeException("Method call exception: " + cause.getMessage(), cause);
        }
        Method method = clazz.getDeclaredMethod(
                "startCLIServer",
                appCls,
                String.class,
                String.class
        );

        method.setAccessible(true);
        method.invoke(null, ctx, env,pidPath);
    }


    public static synchronized void log(String logFilePath, List<String> logs, Throwable throwable) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        try {
            // 1. 确定基础目录
            Path logPath = (logFilePath == null || logFilePath.trim().isEmpty()) ?
                    Paths.get(System.getProperty("java.io.tmpdir"), "testkit-cli.txt") :
                    Paths.get(logFilePath).normalize();

            // 3. 确保父目录 存在
            Files.createDirectories(logPath.getParent());

            // 4. 创建文件（如果不存在）
            if (!Files.exists(logPath)) {
                Files.createFile(logPath);
            }

            // 5. 写入日志（带异常堆栈）
            try (BufferedWriter writer = Files.newBufferedWriter(
                    logPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                String timestamp = LocalDateTime.now().format(formatter);
                writer.write("----" + timestamp + "-----");

                for (String log : logs) {
                    writer.write(log);
                    writer.write("\n");
                }

                if (throwable != null) {
                    writer.write(String.format(
                            "[%s] ERROR - %s%nStacktrace:%n",
                            timestamp, throwable.getMessage()
                    ));
                    for (StackTraceElement element : throwable.getStackTrace()) {
                        writer.write("\tat " + element + "\n");
                    }
                    writer.write("\n"); // 日志分隔线
                }
                writer.flush();
            }
            System.out.println("attach err is write to " + logPath + "\n" + getStackTrace(throwable));
        } catch (Throwable e) {
            System.out.println("log error fail," + getStackTrace(e));
        }
    }


    public static String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }

        while (throwable instanceof InvocationTargetException) {
            throwable = ((InvocationTargetException) throwable).getTargetException();
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);

        // 处理整个异常链
        while (throwable != null) {
            // 打印异常信息
            pw.println(throwable);

            for (StackTraceElement element : throwable.getStackTrace()) {
                pw.println("\tat " + element);
            }

            // 获取下一个 cause
            throwable = throwable.getCause();
            if (throwable != null) {
                pw.println("Caused by: ");
            }
        }

        return sw.toString();
    }
}