package com.testkit.dig;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DigAttach {

    private static final String TARGET_CLASS = "com.testkit.server.TestkitServerManage";
    private static final Logger log = LoggerFactory.getLogger(DigAttach.class);


    public static void agentmain(String args, Instrumentation inst) {
        String logPath = System.getProperty("user.dir") + File.separator + "testkit-dig.txt";
        try {
            System.out.println("Testkit dig accept:" + args);
            Map<String, String> arg = decode(args);
            logPath = arg.get("log_path");
            //帮我解析一些，
            String starterJar = arg.get("starter");
            String ctxGuidingDrug = arg.get("ctx");
            String env = arg.get("env");
            String envKey = arg.get("envKey");
            Integer port = null;
            String portStr = arg.get("port");
            try {
                port = Integer.parseInt(portStr);
            } catch (Throwable ignored) {
                throw new IllegalArgumentException("Testkit dig port must be int," + portStr);
            }

            if (starterJar == null || ctxGuidingDrug == null || port == null) {
                throw new IllegalArgumentException("Testkit dig params not enough," + args);
            }

            // 解析上下文引导信息
            int hashIndex = ctxGuidingDrug.indexOf('#');
            String ctxClsStr = ctxGuidingDrug.substring(0, hashIndex);
            String ctxFieldStr = ctxGuidingDrug.substring(hashIndex + 1);
            Class appCls = findClassLoadedAnywhere(inst, "org.springframework.context.ApplicationContext");
            if (appCls == null) {
                throw new IllegalArgumentException("app Class : org.springframework.context.ApplicationContext not found");
            }
            Class<?> ctxCls = null;
            try {
                ctxCls = appCls.getClassLoader().loadClass(ctxClsStr);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("ctx class not found", e);
            }
            // 获取上下文对象
            Field ctxField = ctxCls.getDeclaredField(ctxFieldStr);
            ctxField.setAccessible(true);
            Object ctx = ctxField.get(null);
            // 分层加载目标类
            Class<?> serverManageClass = loadTargetClass(inst, ctxCls, starterJar);
            // 反射调用核心方法
            invokeServerMethod(appCls, serverManageClass, ctx, env, envKey, port);
        } catch (Throwable t) {
            logError(logPath, t);
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


    // 修改后的核心加载逻辑应如下：
    private static Class<?> loadTargetClass(Instrumentation inst, Class<?> ctxCls, String jarPath)
            throws Exception {

        // 动态开放模块权限
        openInternalPackages(inst);
        Class classLoadedAnywhere = findClassLoadedAnywhere(inst, TARGET_CLASS);
        if (classLoadedAnywhere != null) {
            return classLoadedAnywhere;
        }
        // 阶段2：自定义classloader，然后加入jar，加载TARGET_CLASS
        // 阶段2：创建继承ctxCls类加载器的新类加载器
        try {
            URL jarUrl = new File(jarPath).toURI().toURL();

            // 创建继承ctxCls类加载器的子类加载器
            ClassLoader parentLoader = ctxCls.getClassLoader();
            URLClassLoader childLoader = new URLClassLoader(
                    new URL[]{jarUrl},
                    parentLoader
            ) {
                // 可选：若需要打破双亲委派，可重写loadClass逻辑
//                @Override
//                public Class<?> loadClass(String name) throws ClassNotFoundException {
//                    if (TARGET_CLASS.equals(name)) {
//                        return findClass(name); // 强制优先自己加载目标类
//                    }
//                    return super.loadClass(name);
//                }
            };

            // 尝试用新类加载器加载目标类
            return childLoader.loadClass(TARGET_CLASS);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load class. Please check JAR contents: " + TARGET_CLASS);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid JAR path: " + jarPath);
        }
    }

    private static void openInternalPackages(Instrumentation inst) throws Exception {
        // 获取所有需要开放的包
        String[] internalPackages = {
                "jdk.internal.loader",
                "jdk.internal.reflect",
                "java.lang"
        };

        // 获取java.base模块
        Module baseModule = Object.class.getModule();
        Module agentModule = DigAttach.class.getClassLoader().getUnnamedModule();

        // 动态开放权限
        for (String pkg : internalPackages) {
            if (baseModule.getPackages().contains(pkg)) {
                inst.redefineModule(baseModule,
                        Collections.emptySet(),
                        Collections.singletonMap(pkg, Collections.singleton(agentModule)),
                        Collections.emptyMap(),
                        Collections.emptySet(),
                        Collections.emptyMap()
                );
            }
        }
    }


    private static void addURLToClassLoader(ClassLoader loader, URL url) throws Exception {
        if (loader instanceof URLClassLoader) {
            // Java 8及以下方案
            Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrlMethod.setAccessible(true);
            addUrlMethod.invoke(loader, url);
        } else {
            try {
                // Java 9+ 专用方法
                Class<?> clazz = Class.forName("jdk.internal.loader.BuiltinClassLoader");
                Field ucpField = clazz.getDeclaredField("ucp");
                ucpField.setAccessible(true);
                Object ucp = ucpField.get(loader);
                Method addURLMethod = ucp.getClass().getDeclaredMethod("addURL", URL.class);
                addURLMethod.invoke(ucp, url);
            } catch (ClassNotFoundException | NoSuchFieldException e) {
                throw new RuntimeException("不支持的类加载器: " + loader.getClass().getName());
            }
        }
    }

    private static Class findClassLoadedAnywhere(Instrumentation inst, String className) {
        // 使用Instrumentation检查所有已加载类
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            if (clazz.getName().equals(className)) {
                return clazz;
            }
        }
        return null;
    }

    private static void invokeServerMethod(Class appCls, Class<?> clazz, Object ctx, String env, String envKey, int port)
            throws Exception {

        if (env == null && envKey != null) {
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
        }
        Method method = clazz.getDeclaredMethod(
                "startDigServer",
                appCls,
                String.class,
                int.class
        );

        method.setAccessible(true);
        method.invoke(null, ctx, env, port);
    }


    public static synchronized void logError(String dic, Throwable throwable) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        try {
            // 1. 确定基础目录
            Path logPath = (dic == null || dic.trim().isEmpty()) ?
                    Paths.get(System.getProperty("user.dir"), "testkit-dig.txt") :
                    Paths.get(dic).normalize();

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
                writer.write(String.format(
                        "[%s] ERROR - %s%nStacktrace:%n",
                        timestamp, throwable.getMessage()
                ));

                for (StackTraceElement element : throwable.getStackTrace()) {
                    writer.write("\tat " + element + "\n");
                }
                writer.write("\n"); // 日志分隔线
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