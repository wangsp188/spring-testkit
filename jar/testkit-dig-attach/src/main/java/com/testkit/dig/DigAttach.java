package com.testkit.dig;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.jar.JarFile;

public class DigAttach {
    public static void agentmain(String args, Instrumentation inst) {
        try {
            System.err.println("Testkit Dig接受链接:" + args);
//            args 形似 starter=xx&ctx=xx2&env=xx3&port=89
            //帮我解析一些，
            String[] split = args.split("&");
            String starterJar = null;
            String ctxGuidingDrug = null;
            String env = null;
            String envKey = null;
            Integer port = null;
            for (String kv : split) {
                kv = kv.trim();
                if (kv.startsWith("starter=")) {
                    starterJar = kv.substring("starter=".length());
                    if (starterJar.isEmpty()) {
                        starterJar = null;
                    }
                } else if (kv.startsWith("env=")) {
                    env = kv.substring("env=".length());
                    if (env.isEmpty()) {
                        env = null;
                    }
                } else if (kv.startsWith("envKey=")) {
                    envKey = kv.substring("envKey=".length());
                    if (envKey.isEmpty()) {
                        envKey = null;
                    }
                } else if (kv.startsWith("port=")) {
                    try {
                        port = Integer.parseInt(kv.substring("port=".length()));
                    } catch (Throwable ignored) {
                    }
                } else if (kv.startsWith("ctx=")) {
                    ctxGuidingDrug = kv.substring("ctx=".length());
                    if (ctxGuidingDrug.isEmpty()) {
                        ctxGuidingDrug = null;
                    }
                }
            }

            if (starterJar == null || ctxGuidingDrug == null || port == null) {
                System.err.println("参数不全");
                return;
            }

            // 解析上下文引导信息
            int hashIndex = ctxGuidingDrug.indexOf('#');
            String ctxClsStr = ctxGuidingDrug.substring(0, hashIndex);
            String ctxFieldStr = ctxGuidingDrug.substring(hashIndex + 1);

            // 获取上下文对象
            Class<?> ctxCls = Class.forName(ctxClsStr);
            Field ctxField = ctxCls.getDeclaredField(ctxFieldStr);
            ctxField.setAccessible(true);
            Object ctx = ctxField.get(null);

            // 验证ApplicationContext类型
            ClassLoader guidingLoader = ctxCls.getClassLoader();
            validateApplicationContext(ctx, guidingLoader);

            // 分层加载目标类
            Class<?> serverManageClass = loadTargetClass(inst, starterJar);

            // 反射调用核心方法
            invokeServerMethod(serverManageClass, ctx, env, envKey, port);
        } catch (Throwable t) {
            t.printStackTrace(); // 打印完整堆栈
        }
    }

    private static void validateApplicationContext(Object ctx, ClassLoader loader)
            throws ClassNotFoundException {
        Class<?> appCls = loader.loadClass("org.springframework.context.ApplicationContext");
        if (!appCls.isAssignableFrom(ctx.getClass())) {
            throw new RuntimeException("Context对象不是ApplicationContext实例");
        }
    }

    private static void addToClasspath(Instrumentation inst, String jarPath)
            throws Exception {
        JarFile jar = new JarFile(new File(jarPath));
        inst.appendToSystemClassLoaderSearch(jar);
    }

    // 修改后的核心加载逻辑应如下：
    private static Class<?> loadTargetClass(Instrumentation inst, String jarPath)
            throws Exception {
        final String TARGET_CLASS = "com.testkit.server.TestkitServerManage";

        // 阶段1：检查是否已存在类（任何加载器加载过）
        Class classLoadedAnywhere = findClassLoadedAnywhere(inst, TARGET_CLASS);
        if (classLoadedAnywhere != null) {
            return classLoadedAnywhere;
        }
        // 阶段2：强制使用系统加载器（确保appendToSystem生效）
        try {
            addToClasspath(inst, jarPath);
            // 显式使用系统类加载器（此时已添加新JAR）
            return Class.forName(TARGET_CLASS, true, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e) {
            // 当且仅当JAR未正确添加时会走到这里
            throw new RuntimeException("系统类加载器加载失败，请检查JAR是否包含类: " + TARGET_CLASS);
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

    private static void invokeServerMethod(Class<?> clazz, Object ctx, String env, String envKey, int port)
            throws Exception {
        Class<?> ctxClass = ctx.getClass();
        Class<?> appCtxClass = ctxClass.getClassLoader()
                .loadClass("org.springframework.context.ApplicationContext");

        if (env == null && envKey != null) {
            try {
                // 获取getEnvironment方法（无参数）
                Method getEnvMethod = appCtxClass.getMethod("getEnvironment");

                // 调用getEnvironment方法获取环境对象
                Object environment = getEnvMethod.invoke(ctx);

                // 获取getProperty方法（String参数）
                Method getPropertyMethod = environment.getClass()
                        .getMethod("getProperty", String.class);

                // 调用getProperty方法获取配置值
                env = (String) getPropertyMethod.invoke(environment, envKey);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Spring环境方法不存在，请确认Spring版本（需要4.0+）", e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("方法访问权限异常，请检查SecurityManager配置", e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                throw new RuntimeException("方法调用异常: " + cause.getMessage(), cause);
            }
        }
        Method method = clazz.getDeclaredMethod(
                "startDigServer",
                appCtxClass,
                String.class,
                int.class
        );

        method.setAccessible(true);
        method.invoke(null, ctx, env, port);
    }
}