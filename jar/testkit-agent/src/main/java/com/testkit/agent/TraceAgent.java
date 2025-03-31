package com.testkit.agent;

import javassist.*;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TraceAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        TraceConfig config = new TraceConfig();
        try {
            String base64Json = URLDecoder.decode(agentArgs, "UTF-8");
            String json = new String(Base64.getDecoder().decode(base64Json), "UTF-8");
            Map<String, String> jsonObject = decode(json);
            String packages = jsonObject.get("packages");
            if (packages != null && !packages.isEmpty()) {
                config.setPackages(Arrays.stream(packages.split(",")).distinct().map(new Function<String, String>() {
                    @Override
                    public String apply(String s) {
                        return s.trim();
                    }
                }).collect(Collectors.toSet()));
            }

            String clsSuffix = jsonObject.get("clsSuffix");
            if (clsSuffix != null && !clsSuffix.isEmpty()) {
                config.setClsSuffix(Arrays.stream(clsSuffix.split(",")).distinct().map(new Function<String, String>() {
                    @Override
                    public String apply(String s) {
                        return s.trim();
                    }
                }).collect(Collectors.toSet()));
            }

            String whites = jsonObject.get("whites");
            if (whites != null && !whites.isEmpty()) {
                config.setWhites(Arrays.stream(whites.split(",")).distinct().map(new Function<String, String>() {
                    @Override
                    public String apply(String s) {
                        return s.trim();
                    }
                }).collect(Collectors.toSet()));
            }

            String blacks = jsonObject.get("blacks");
            if (blacks != null && !blacks.isEmpty()) {
                config.setBlacks(Arrays.stream(blacks.split(",")).distinct().map(new Function<String, String>() {
                    @Override
                    public String apply(String s) {
                        return s.trim();
                    }
                }).collect(Collectors.toSet()));
            }
            config.setTraceWeb("true".equals(jsonObject.get("traceWeb")));
            config.setTraceMybatis("true".equals(jsonObject.get("traceMybatis")));
            config.setLogMybatis("true".equals(jsonObject.get("logMybatis")));
            System.err.println("Testkit trace config: " + config);
        } catch (Throwable e) {
            System.err.println("Testkit trace config parse error");
            e.printStackTrace();
            return;
        }
        String allPath = System.getProperty("java.class.path");
        ClassPool classPool = ClassPool.getDefault();
        classPool.appendClassPath(new LoaderClassPath(ClassLoader.getSystemClassLoader()));

        // 根据系统的路径分隔符拆分类路径
        String[] paths = allPath.split(File.pathSeparator);

        // 将每个路径添加到 ClassPool 中
        for (String path : paths) {
            try {
                classPool.appendClassPath(path);
            } catch (NotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if ("java/util/concurrent/ThreadPoolExecutor".equals(className) || "java/util/concurrent/ScheduledExecutorService".equals(className)) {
                    return enhanceThreadPoolExecutor(classPool, classfileBuffer);
                } else if (config.isTraceWeb() && "org/springframework/web/servlet/DispatcherServlet".equals(className)) {
                    return enhanceWebDispatcher(classPool, classfileBuffer);
                } else if (config.isTraceMybatis() && "org/apache/ibatis/plugin/InterceptorChain".equals(className)) {
                    return enhanceMybatis(classPool, classfileBuffer, config.isLogMybatis());
                }
                if (classBeingRedefined != null && classBeingRedefined.isEnum()) {
                    return null;
                }
                String group = ismatch(className, config);
                if (group == null) {
                    return null;
                }

                return enhanceClass(classPool, group, className.substring(className.lastIndexOf("/") + 1), classfileBuffer);
            }
        }, true);
        if (inst.isRetransformClassesSupported()) {
            try {
                Class<?> aClass = Class.forName("java.util.concurrent.ThreadPoolExecutor");
                inst.retransformClasses(aClass);
                aClass = Class.forName("java.util.concurrent.ScheduledExecutorService");
                inst.retransformClasses(aClass);
            } catch (Throwable e) {
                System.err.println("Testkit trace retransformThreadPoolExecutor class error");
                e.printStackTrace();
            }
        }
    }

    private static byte[] enhanceWebDispatcher(ClassPool classPool, byte[] classfileBuffer) {
        try {
            CtClass ctClass = classPool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
            CtMethod method = null;
            try {
                method = ctClass.getDeclaredMethod("doService", new CtClass[]{classPool.get("javax.servlet.http.HttpServletRequest"),
                        classPool.get("javax.servlet.http.HttpServletResponse")});
            } catch (NotFoundException e) {
                method = ctClass.getDeclaredMethod("doService", new CtClass[]{classPool.get("jakarta.servlet.http.HttpServletRequest"),
                        classPool.get("jakarta.servlet.http.HttpServletResponse")});
            }

            // 开始对方法进行增强
            method.addLocalVariable("_t_t_", classPool.get("com.testkit.trace.TraceInfo"));


            // execute(Runnable)
            // 在方法前后插入日志
            method.insertBefore("{ _t_t_ = new com.testkit.trace.TraceInfo(com.testkit.trace.TraceInfo.getCurrent(),\"DispatcherServlet\",$1.getMethod(),$1.getServletPath()).stepIn(); }");
            method.insertAfter("{ System.err.println(\"TRACE_PROFILER - \" + _t_t_.stepOut(null, null, java.lang.String.valueOf($2.getStatus()),null).toProfilerString()); }");

            method.addCatch(
                    "{ com.testkit.trace.TraceInfo _t_t_1 = com.testkit.trace.TraceInfo.getCurrent();" +
                            "    if (_t_t_1 != null) {" +
                            "        System.err.println(\"TRACE_PROFILER - \" + _t_t_1.stepOut(null, $e,java.lang.String.valueOf($2.getStatus()),null).toProfilerString());" +
                            "    }" +
                            "    throw $e;" +
                            "}",
                    ClassPool.getDefault().get("java.lang.Throwable"));

            byte[] enhancedByteCode = ctClass.toBytecode();
            ctClass.detach();
            return enhancedByteCode;
        } catch (Exception e) {
            System.err.println("Testkit javassist enhance fail: DispatcherServlet errorType:" + e.getClass().getName() + ":" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] enhanceMybatis(ClassPool classPool, byte[] classfileBuffer, boolean logMybatis) {
        try {
            CtClass ctClass = classPool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
            CtMethod method = ctClass.getDeclaredMethod("pluginAll", new CtClass[]{classPool.get("java.lang.Object")});
            // 我想在pluginAll最后修改返回值为 new com.testkit.server.enhance.TestkitMybatisInterceptor().plugin(当前返回值)
            // 修改返回值
            if (logMybatis) {
                method.insertAfter("{" +
                        "if ($_ instanceof org.apache.ibatis.executor.Executor) {" +
                        "  $_ = com.testkit.server.enhance.TestkitMybatisInterceptor.pluginExecutorWithSql($_);" +
                        "}" +
                        "}");
            } else {
                method.insertAfter("{" +
                        "if ($_ instanceof org.apache.ibatis.executor.Executor) {" +
                        "  $_ = com.testkit.server.enhance.TestkitMybatisInterceptor.pluginExecutor($_);" +
                        "}" +
                        "}");
            }

            byte[] enhancedByteCode = ctClass.toBytecode();
            ctClass.detach();
            return enhancedByteCode;
        } catch (Exception e) {
            System.err.println("Testkit javassist enhance fail: InterceptorChain errorType:" + e.getClass().getName() + ":" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] enhanceThreadPoolExecutor(ClassPool classPool, byte[] classfileBuffer) {
        try {
            CtClass ctClass = classPool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
            // 增强 submit 方法
            for (CtMethod method : ctClass.getDeclaredMethods()) {
                if ("execute".equals(method.getName())) {
                    enhanceExecuteMethod(classPool, method);
                } else if ("submit".equals(method.getName())) {
                    enhanceSubmitMethod(classPool, method);
                } else if ("invokeAll".equals(method.getName())) {
                    enhanceInvokeAllMethod(classPool, method);
                } else if ("invokeAny".equals(method.getName())) {
                    enhanceInvokeAnyMethod(classPool, method);
                }
            }

            byte[] enhancedByteCode = ctClass.toBytecode();
            ctClass.detach();
            return enhancedByteCode;
        } catch (Exception e) {
            System.err.println("Testkit javassist enhance fail: ThreadPoolExecutor/ScheduledExecutorService errorType:" + e.getClass().getName() + ":" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static void enhanceExecuteMethod(ClassPool classPool, CtMethod method) throws CannotCompileException, NotFoundException {
        CtClass[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 1 && "java.lang.Runnable".equals(parameterTypes[0].getName())) {
            // execute(Runnable)
            method.insertBefore(
                    "{ $1 = com.testkit.trace.TraceThreadContextTransferTool.wrap((java.lang.Runnable)$1); }"
            );
        }

    }

    private static void enhanceInvokeAllMethod(ClassPool classPool, CtMethod method) throws CannotCompileException, NotFoundException {
        CtClass[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 1 && "java.util.Collection".equals(parameterTypes[0].getName())) {
            // execute(Runnable)
            method.insertBefore(
                    "{ $1 = com.testkit.trace.TraceThreadContextTransferTool.wrapCalls((java.util.Collection)$1); }"
            );
        }

    }

    private static void enhanceInvokeAnyMethod(ClassPool classPool, CtMethod method) throws CannotCompileException, NotFoundException {
        CtClass[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 1 && "java.util.Collection".equals(parameterTypes[0].getName())) {
            // execute(Runnable)
            method.insertBefore(
                    "{ $1 = com.testkit.trace.TraceThreadContextTransferTool.wrapCalls((java.util.Collection)$1); }"
            );
        }
    }

    private static void enhanceSubmitMethod(ClassPool classPool, CtMethod method) throws CannotCompileException, NotFoundException {
        CtClass[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 1 && "java.lang.Runnable".equals(paramTypes[0].getName())) {
            // submit(Runnable)
            method.insertBefore(
                    "{ $1 = com.testkit.trace.TraceThreadContextTransferTool.wrap((java.lang.Runnable)$1); }"
            );
        } else if (paramTypes.length == 2 && "java.lang.Runnable".equals(paramTypes[0].getName()) && "java.lang.Object".equals(paramTypes[1].getName())) {

            // submit(Runnable, T)
            method.insertBefore(
                    "{ $1 = com.testkit.trace.TraceThreadContextTransferTool.wrap($1); }"
            );
        } else if (paramTypes.length == 1 && "java.util.concurrent.Callable".equals(paramTypes[0].getName())) {

            // submit(Callable)
            method.insertBefore(
                    "{ $1 = com.testkit.trace.TraceThreadContextTransferTool.wrap((java.util.concurrent.Callable)$1); }"
            );
        }
    }


    private static String ismatch(String className, TraceConfig config) {
        if (className.contains("$")) {
            return null;
        }
        className = className.replace('/', '.');
        // 检查白名单
        if (config.whites.contains(className)) {
            return "white"; // 如果白名单中包含此类，直接认为匹配
        }

        // 检查黑名单
        if (config.blacks.contains(className)) {
            return null; // 如果黑名单中包含此类，直接不匹配
        }

        // 检查包名匹配
        for (String pkg : config.packages) {
            if (className.startsWith(pkg)) {
                // 检查类后缀匹配
                for (String suffix : config.clsSuffix) {
                    if (className.endsWith(suffix)) {
                        return suffix; // 包名和后缀都匹配
                    }
                }
            }
        }
        return null; // 如果没有匹配，返回不匹配
    }

    private static byte[] enhanceClass(ClassPool classPool, String group, String className, byte[] classfileBuffer) {
        String methodSign = null;
        try {
            CtClass ctClass = classPool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));

            // 跳过接口和抽象类
            if (ctClass.isInterface() || ctClass.isEnum() || Modifier.isAbstract(ctClass.getModifiers())) {
                return null;
            }


            for (CtMethod method : ctClass.getDeclaredMethods()) {
                // 跳过抽象方法
                if (!Modifier.isPublic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers()) || isGeneratedMethod(method.getName())) {
                    continue;
                }

                methodSign = method.getName() + "(" + method.getSignature() + ")";
                enhanceMethod(classPool, method, group, className);
            }

            byte[] enhancedByteCode = ctClass.toBytecode();
            ctClass.detach(); // 防止内存泄漏
            return enhancedByteCode;
        } catch (Throwable e) {
            e.printStackTrace();
            System.err.println("Testkit javassist enhance fail:" + className + "," + methodSign + " errorType:" + e.getClass().getName() + ":" + e.getMessage());
            return null;
        }

    }

    private static boolean isGeneratedMethod(String methodName) {
        // 判断是否为 Lambda 表达式的方法
//        if (methodName.contains("$$Lambda$")) {
//            return true;
//        }
        if (methodName.contains("lambda$")) {
            return true;
        }

//        // 判断是否为类中编译生成的方法
//        if (methodName.matches(".*\\$\\d+$")) {
//            return true;
//        }

        return false;
    }


    private static void enhanceMethod(ClassPool classPool, CtMethod method, String group, String className) throws CannotCompileException, NotFoundException {
        String methodName = method.getName();
        // 开始对方法进行增强
        method.addLocalVariable("_t_t_", classPool.get("com.testkit.trace.TraceInfo"));
// 在方法开始处插入 stepIn
        method.insertBefore(
                "_t_t_ = com.testkit.trace.TraceInfo.getCurrent();" +
                        "if (_t_t_ != null) {" +
                        "    _t_t_ = new com.testkit.trace.TraceInfo(_t_t_,\"" + group + "\",\"" + className + "\", \"" + methodName + "\").stepIn();" +
                        "}"
        );

        // 获取方法的返回类型
        CtClass returnType = method.getReturnType();

        // 添加后置逻辑
        if (returnType.equals(CtClass.voidType)) {
            method.insertAfter(
                    "{ if (_t_t_ != null) {" +
                            "    System.err.println(_t_t_.stepOut(null, null));" + // void 方法的默认返回值
                            "} }"
            );
        } else if (returnType.isArray()) {
            // 处理数组类型，不区分是否基本类型数组，因为数组都可以作为 Object 处理
            method.insertAfter(
                    "{ if (_t_t_ != null) {" +
                            "    System.err.println(_t_t_.stepOut($_, null));" +
                            "} }"
            );
        } else if (returnType.isPrimitive()) {
            // 基本类型返回值
            method.insertAfter(
                    "{ if (_t_t_ != null) {" +
                            "    System.err.println(_t_t_.stepOut(($w)$_, null));" + // $w 表示基本类型自动装箱
                            "} }"
            );
        } else {
            method.insertAfter(
                    "{ if (_t_t_ != null) {" +
                            "    System.err.println(_t_t_.stepOut((Object)$_, null));" + // 显式类型转换
                            "} }"
            );
        }

        // 在异常捕获处插入 stepOut，增加非空检查
        method.addCatch(
                "{ com.testkit.trace.TraceInfo _t_t_1 = com.testkit.trace.TraceInfo.getCurrent();" +
                        "    if (_t_t_1 != null) {" +
                        "        System.err.println(_t_t_1.stepOut(null, $e));" +
                        "    }" +
                        "    throw $e;" +
                        "}",
                ClassPool.getDefault().get("java.lang.Throwable")
        );
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


    public static class TraceConfig {

        private boolean traceWeb = true;
        private boolean traceMybatis = true;
        private boolean logMybatis = true;

        private Set<String> packages = new HashSet<>();

        private Set<String> clsSuffix = new HashSet<>();

        private Set<String> whites = new HashSet<>();

        private Set<String> blacks = new HashSet<>();


        public Set<String> getPackages() {
            return packages;
        }

        public void setPackages(Set<String> packages) {
            this.packages = packages;
        }

        public Set<String> getClsSuffix() {
            return clsSuffix;
        }

        public void setClsSuffix(Set<String> clsSuffix) {
            this.clsSuffix = clsSuffix;
        }

        public Set<String> getWhites() {
            return whites;
        }

        public void setWhites(Set<String> whites) {
            this.whites = whites;
        }

        public Set<String> getBlacks() {
            return blacks;
        }

        public void setBlacks(Set<String> blacks) {
            this.blacks = blacks;
        }

        public boolean isTraceWeb() {
            return traceWeb;
        }

        public void setTraceWeb(boolean traceWeb) {
            this.traceWeb = traceWeb;
        }

        public boolean isTraceMybatis() {
            return traceMybatis;
        }

        public void setTraceMybatis(boolean traceMybatis) {
            this.traceMybatis = traceMybatis;
        }

        public boolean isLogMybatis() {
            return logMybatis;
        }

        public void setLogMybatis(boolean logMybatis) {
            this.logMybatis = logMybatis;
        }
    }
}