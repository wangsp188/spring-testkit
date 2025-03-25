package com.testkit.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.testkit.trace.TraceInfo;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class TestkitServer {

    private ApplicationContext app;

    private SpringCacheTool springCacheTool;

    private FlexibleTestTool flexibleTestTool;

    private FunctionCallTool functionCallTool;

    private ViewValueTool viewValueTool;

    private HttpServer server;

    private String project;
    private String appName;
    private String env;
    private boolean enableTrace;


    public TestkitServer(ApplicationContext app, String project, String appName, String env, boolean enableTrace) {
        if (RuntimeAppHelper.needRegister(env) && (project == null || project.isEmpty() || appName == null || appName.isEmpty())) {
            throw new IllegalArgumentException("project/appName can not be empty");
        }
        this.app = app;
        this.springCacheTool = new SpringCacheTool(app);
        this.flexibleTestTool = new FlexibleTestTool(app);
        this.functionCallTool = new FunctionCallTool(app);
        this.viewValueTool = new ViewValueTool(app);
        this.project = project;
        this.appName = appName;
        this.env = env;
        this.enableTrace = enableTrace;
    }


    public synchronized int start() {
        this.server = startHttpServer(app);
        return this.server.getAddress().getPort();
    }

    public synchronized void stop() {
        if (this.server == null) {
            return;
        }
        int port = this.server.getAddress().getPort();
        try {
            this.server.stop(0);
        } catch (Throwable ignore) {
        }
        if (Objects.equals(RuntimeAppHelper.LOCAL, env)) {
            RuntimeAppHelper.removeApp(project, appName, port);
        }
    }

    private HttpServer startHttpServer(ApplicationContext app) {
        int serverPort = 0;
        if (app instanceof WebServerApplicationContext) {
            int port = ((WebServerApplicationContext) app).getWebServer().getPort();
            serverPort = port + 10000;
        } else {
            serverPort = findAvailablePort(30001, 30099);
        }
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(serverPort), 0);
        } catch (IOException e) {
            throw new RuntimeException("fail to create testkit server", e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    returnError(exchange, "un support http method");
                    return;
                }
                try {
                    InputStream inputStream = exchange.getRequestBody();
                    // 读取请求的JSON内容
                    String content = readInputStream(inputStream);
                    Req req = null;
                    try {
                        req = ReflexUtils.SIMPLE_MAPPER.readValue(content, Req.class);
                    } catch (JsonProcessingException e) {
                        throw new TestkitException("parse req error," + e.getMessage());
                    }
                    Ret ret = null;
                    if ("hello".equals(req.getMethod())) {
                        ret = Ret.success(enableTrace);
                    } else if ("get_task_ret".equals(req.getMethod())) {
                        Map<String, String> params = req.getParams();
                        String reqId = params.get("reqId");
                        String timeoutStr = params.get("timeout");
                        int timeout = 86400;
                        if (timeoutStr != null && !timeoutStr.isEmpty()) {
                            timeout = Integer.parseInt(timeoutStr);
                        }
                        ret = TaskManager.getResult(reqId, timeout);
                    } else if ("stop_task".equals(req.getMethod())) {
                        Map<String, String> params = req.getParams();
                        String reqId = params.get("reqId");
                        ret = Ret.success(TaskManager.stopTask(reqId));
                        System.err.println("Testkit stopReq reqId:" + reqId + " cancel:" + ret.getData());
                    } else {
                        String reqId = TaskManager.generateRandomString(16);
                        Req finalReq = req;
                        TaskManager.startTask(reqId, new Callable<Ret>() {
                            @Override
                            public Ret call() throws Exception {
                                return processReq(reqId, finalReq);
                            }
                        });
                        ret = Ret.success(reqId);
                        System.err.println("Testkit submitReq  method:" + req.getMethod() + " reqId:" + reqId);
                    }

                    String jsonResponse = null;
                    try {
                        jsonResponse = ReflexUtils.SIMPLE_MAPPER.writeValueAsString(ret);
                    } catch (Throwable e) {
                        //这里对象序列化失败，可以判断
//                        如果是array则每个对象.toString拼接出来
//                        如果是collection则每个对象.toString拼接出来
//                        如果是map，则key和val都toSTring
                        try {
                            Object obj = ret.getData();
                            obj = obj == null ? "null" : obj;
                            if (obj.getClass().isArray()) {
                                // 如果是array
                                Object[] array = (Object[]) obj;
                                for (int i = 0; i < array.length; i++) {
                                    array[i] = String.valueOf(array[i]);
                                }
                            } else if (obj instanceof Collection) {
                                // 如果是collection
                                Collection<?> collection = (Collection<?>) obj;
                                Collection<String> newCollection = new ArrayList<>();
                                for (Object element : collection) {
                                    newCollection.add(String.valueOf(element));
                                }
                                ret.setData(newCollection);
                            } else if (obj instanceof Map) {
                                // 如果是map
                                Map<?, ?> map = (Map<?, ?>) obj;
                                Map<String, String> newMap = new HashMap<>();
                                for (Map.Entry<?, ?> entry : map.entrySet()) {
                                    newMap.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                                }
                                ret.setData(newMap);
                            } else {
                                // 其他情况
                                ret.setData(String.valueOf(obj));
                            }
                            jsonResponse = ReflexUtils.SIMPLE_MAPPER.writeValueAsString(ret);
                        } catch (Throwable ex) {
                            throw new TestkitException("ret serialize json error," + ex.getMessage());
                        }
                    }
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
                    OutputStream outputStream = exchange.getResponseBody();
                    outputStream.write(jsonResponse.getBytes());
                    outputStream.close();
                } catch (Throwable e) {
                    System.err.println("server error" + e);
                    e.printStackTrace();
                    returnError(exchange, getNoneTestkitStackTrace(e));
                }
            }
        });
        new Thread(() -> {
            try {
                server.start();
                if (RuntimeAppHelper.needRegister(env)) {
                    RuntimeAppHelper.addApp(project, appName, server.getAddress().getPort());
                }

                System.err.println("————————————————————————————————————————————————————————————————————\n" +
                        "//                          _ooOoo_                               //\n" +
                        "//                         o8888888o                              //\n" +
                        "//                         88\" . \"88                              //\n" +
                        "//                         (| ^_^ |)                              //\n" +
                        "//                         O\\  =  /O                              //\n" +
                        "//                      ____/`---'\\____                           //\n" +
                        "//                    .'  \\\\|     |//  `.                         //\n" +
                        "//                   /  \\\\|||  :  |||//  \\                        //\n" +
                        "//                  /  _||||| -:- |||||-  \\                       //\n" +
                        "//                  |   | \\\\\\  -  /// |   |                       //\n" +
                        "//                  | \\_|  ''\\---/''  |   |                       //\n" +
                        "//                  \\  .-\\__  `-`  ___/-. /                       //\n" +
                        "//                ___`. .'  /--.--\\  `. . ___                     //\n" +
                        "//              .\"\" '<  `.___\\_<|>_/___.'  >'\"\".                  //\n" +
                        "//            | | :  `- \\`.;`\\ _ /`;.`/ - ` : | |                 //\n" +
                        "//            \\  \\ `-.   \\_ __\\ /__ _/   .-` /  /                 //\n" +
                        "//      ========`-.____`-.___\\_____/___.-`____.-'========         //\n" +
                        "//                           `=---='                              //\n" +
                        "//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^        //\n" +
                        "//            佛祖保佑         永不宕机     永无BUG                  //\n" +
                        "————————————————————————————————————————————————————————————————————\n" +
                        "                         Testkit:" +  server.getAddress().getPort());
            } catch (Exception e) {
                System.err.println("Testkit server started fail on port " +  server.getAddress().getPort() + " " + e);
            }
        }).start();
        return server;
    }


    private Ret processReq(String reqId, Req req) {
        Class interceptorType = ReflexUtils.compile(req.getInterceptor());
        Object interceptorObj = null;
        Method beforeMethod = null;
        Method afterMethod = null;
        if (interceptorType != null) {
            try {
                beforeMethod = interceptorType.getDeclaredMethod("invokeBefore", String.class, String.class, Map.class);
                beforeMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
            }

            try {
                afterMethod = interceptorType.getDeclaredMethod("invokeAfter", String.class, String.class, Map.class, Integer.class, Object.class, Throwable.class);
                afterMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
            }

            if (beforeMethod != null || afterMethod != null) {
                //        interceptorObj and inject
                try {
                    Constructor declaredConstructor = interceptorType.getDeclaredConstructor();
                    declaredConstructor.setAccessible(true);
                    interceptorObj = declaredConstructor.newInstance();
                } catch (Throwable e) {
                    throw new TestkitException(interceptorType.getName() + "interceptor error, class must has none params public constructor," + e.getMessage());
                }
                try {
                    ReflexUtils.autowireBean(app.getAutowireCapableBeanFactory(), interceptorObj);
                } catch (Throwable e) {
                    throw new TestkitException("interceptorObj autowireBean error," + e.getMessage());
                }
            }
        }


        Map<String, String> params = req.getParams();
        params.put("req_id", reqId);
        Object ret = null;
        Throwable error = null;
        Long begin = null;
        TraceInfo testkitTraceInfo = null;
        Map<String, String> profile = new HashMap<>();
        try {
            if (beforeMethod != null) {
                beforeMethod.invoke(interceptorObj, env, req.getMethod(), params);
            }

            begin = System.currentTimeMillis();
            if (req.isTrace()) {
                String biz = "unknown";
                String action = "unknown";
                switch (req.getMethod()) {
                    case "spring-cache":
                        biz = params.get("typeClass");
                        if (biz.contains(".")) {
                            biz = biz.substring(biz.lastIndexOf(".") + 1);
                        }
                        action = params.get("methodName");
                        break;
                    case "function-call":
                        biz = params.get("typeClass");
                        if (biz.contains(".")) {
                            biz = biz.substring(biz.lastIndexOf(".") + 1);
                        }
                        action = params.get("methodName");
                        break;
                    case "flexible-test":
                        biz = "dynamic_code";
                        action = params.get("methodName");
                        break;
                }

                testkitTraceInfo = TraceInfo.buildRoot(reqId, req.getMethod(), biz, action).stepIn();
                profile.put("req_id", testkitTraceInfo.getReqid());
            }
            try {
                switch (req.getMethod()) {
                    case "spring-cache":
                        ret = springCacheTool.process(params);
                        break;
                    case "flexible-test":
                        ret = flexibleTestTool.process(params);
                        break;
                    case "function-call":
                        ret = functionCallTool.process(params);
                        break;
                    case "view-value":
                        ret = viewValueTool.process(params);
                        break;
                    default:
                        throw new TestkitException("un support method");
                }
                if (req.isTrace()) {
                    profile.put("link", testkitTraceInfo.stepOut(ret, null).toProfilerString());
                    profile.put("cost", String.valueOf((System.currentTimeMillis() - begin)));
                }
                return Ret.success(ret, profile);
            } catch (Throwable e) {
                if (req.isTrace()) {
                    profile.put("link", testkitTraceInfo.stepOut(null, e).toProfilerString());
                    profile.put("cost", String.valueOf((System.currentTimeMillis() - begin)));
                }
                throw e;
            } finally {
                if (profile.containsKey("link")) {
                    System.err.println("TRACE_PROFILER - " + profile.get("link"));
                }
            }
        } catch (Throwable e) {
            System.err.println("TESTKIT tool execute error, errorType:" + e.getClass().getName() + ", " + e.getMessage());
            e.printStackTrace();
            error = e;
            return Ret.fail(getNoneTestkitStackTrace(e), profile);
        } finally {
            Integer cost = begin == null ? null : Math.toIntExact((System.currentTimeMillis() - begin));
            while (error instanceof InvocationTargetException) {
                error = ((InvocationTargetException) error).getTargetException();
            }
            System.err.println("TESTKIT_DETAIL tool:" + req.getMethod() + " cost:" + cost + " params:" + params + " ret:" + ret + " e:" + error);
            if (afterMethod != null) {
                try {
                    afterMethod.invoke(interceptorObj, env, req.getMethod(), params, cost, ret, error);
                } catch (Throwable e) {
                    System.out.println("TESTKIT interceptor invokeAfter error, errorType:" + e.getClass().getName() + ", " + e.getMessage());
                }
            }
        }
    }

    public static String getNoneTestkitStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        if (throwable instanceof TestkitException) {
            return throwable.getMessage();
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
                if (element.getClassName().startsWith("com.testkit")) {
                    break;
                }
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

    private static String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }

    private static void returnError(HttpExchange exchange, String message) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        Ret ret = new Ret();
        ret.setSuccess(false);
        ret.setMessage(message);
        byte[] bytes = ReflexUtils.SIMPLE_MAPPER.writeValueAsBytes(ret);
        exchange.sendResponseHeaders(200, bytes.length); // 405 Method Not Allowed
        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(bytes);
        outputStream.close();
    }


    /**
     * 检查本地 TCP 端口是否可用（所有网络接口）
     */
    public static boolean isTcpPortAvailable(int port) {
        return isTcpPortAvailable(port, null);
    }

    /**
     * 检查指定地址的 TCP 端口是否可用
     */
    public static boolean isTcpPortAvailable(int port, String bindAddress) {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            InetSocketAddress address = (bindAddress == null) ?
                    new InetSocketAddress(port) :
                    new InetSocketAddress(bindAddress, port);
            serverSocket.bind(address);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 查找指定范围内的可用端口
     */
    public static int findAvailablePort(int minPort, int maxPort) {
        for (int port = minPort; port <= maxPort; port++) {
            if (isTcpPortAvailable(port)) {
                return port;
            }
        }
        throw new RuntimeException("No available ports between " + minPort + "-" + maxPort);
    }


    public SpringCacheTool getSpringCacheTool() {
        return springCacheTool;
    }

    public void setSpringCacheTool(SpringCacheTool springCacheTool) {
        this.springCacheTool = springCacheTool;
    }

    public FlexibleTestTool getFlexibleTestTool() {
        return flexibleTestTool;
    }

    public void setFlexibleTestTool(FlexibleTestTool flexibleTestTool) {
        this.flexibleTestTool = flexibleTestTool;
    }

    public FunctionCallTool getFunctionCallTool() {
        return functionCallTool;
    }

    public void setFunctionCallTool(FunctionCallTool functionCallTool) {
        this.functionCallTool = functionCallTool;
    }

    public ViewValueTool getViewValueTool() {
        return viewValueTool;
    }

    public void setViewValueTool(ViewValueTool viewValueTool) {
        this.viewValueTool = viewValueTool;
    }

    public ApplicationContext getApp() {
        return app;
    }

    public void setApp(ApplicationContext app) {
        this.app = app;
    }

    public HttpServer getServer() {
        return server;
    }

    public void setServer(HttpServer server) {
        this.server = server;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public boolean isEnableTrace() {
        return enableTrace;
    }

    public void setEnableTrace(boolean enableTrace) {
        this.enableTrace = enableTrace;
    }

}
