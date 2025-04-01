package com.testkit.dig;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.sun.tools.attach.*;

import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class TestkitDigGuide {

    private static final String RESET = "\033[0m";
    private static final String GREEN = "\033[1;32m";  // 亮绿色
    private static final String YELLOW = "\033[1;33m"; // 亮黄色
    private static final String RED = "\033[1;31m";    // 亮红色

    private static final String LIB_DIR = "libs/";
    public static final String EXIT = "exit";
    private static volatile boolean isServerRunning = true;
    private static final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();


    public static void main(String[] args) throws Exception {
        // 修改输出流编码
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8.name());
        System.setOut(out);
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        int port = 30999;
        String portPro = System.getProperty("testkit.dig.port");
        if (portPro != null) {
            try {
                port = Integer.parseInt(portPro);
            } catch (NumberFormatException e) {
                throw new RuntimeException("testkit.dig.port must be int", e);
            }
        }

        System.out.println(GREEN + "___________              __   __   .__  __    ________  .__        \n" +
                "\\__    ___/___   _______/  |_|  | _|__|/  |_  \\______ \\ |__| ____  \n" +
                "  |    |_/ __ \\ /  ___/\\   __\\  |/ /  \\   __\\  |    |  \\|  |/ ___\\ \n" +
                "  |    |\\  ___/ \\___ \\  |  | |    <|  ||  |    |    `   \\  / /_/  >\n" +
                "  |____| \\___  >____  > |__| |__|_ \\__||__|   /_______  /__\\___  / \n" +
                "             \\/     \\/            \\/                  \\/  /_____/  " + RESET);
        AppMeta runningApp = findRunningApp(port);
        if (runningApp == null) {

            // 加载嵌入的JAR文件
            File attachJar = getEmbeddedJar("testkit-dig-attach-1.0.jar");
            File starterJar = getEmbeddedJar("testkit-starter-1.0.jar");
            attachJar.deleteOnExit();
            starterJar.deleteOnExit();
            // 列出所有JVM进程4
            List<VirtualMachineDescriptor> vmList = VirtualMachine.list();
            vmList = vmList.stream().filter(new Predicate<VirtualMachineDescriptor>() {
                @Override
                public boolean test(VirtualMachineDescriptor virtualMachineDescriptor) {
                    String displayName = virtualMachineDescriptor.displayName();
                    return !displayName.contains(TestkitDigGuide.class.getName())
                            && !displayName.contains("IntelliJ IDEA.app")
                            && !displayName.contains("com.intellij.idea.Main")
                            && !displayName.contains("testkit-dig-1.0.jar")
                            ;
                }
            }).toList();
            if (vmList.isEmpty()) {
                System.out.println("No virtual machine found");
                return;
            }

            runningApp = tryStartServer(vmList, br, starterJar.getAbsolutePath(), port, attachJar.getAbsolutePath());
        }

        System.out.println("Connect success: " + JSON.toJSONString(runningApp));
        // 启动交互式命令行
        startCommandLoop(br, port);
    }


    private static AppMeta findRunningApp(int port) {
        HashMap<String, String> requestData = new HashMap<>();
        requestData.put("method", "hello");
        try {
            JSONObject response = HttpUtil.sendPost("http://localhost:" + port + "/", requestData, JSONObject.class);
            if (!response.getBooleanValue("success")) {
                return null;
            }
            return response.getObject("data", AppMeta.class);
        } catch (Exception e) {
            //判断
            if (!HttpUtil.isTcpPortAvailable(port)) {
                throw new IllegalArgumentException("The port is occupied. pls change vmOptions change port, for example -Dtestkit.dig.port=10086");
            }
            return null;
        }
    }

    private static void startHeartbeatCheck(int port) {
        heartbeatExecutor.scheduleWithFixedDelay(() -> {
            try {
                AppMeta app = findRunningApp(port);
                if (app == null) {
                    System.err.println("[Heartbeat] Server lost, shutting down...");
                    isServerRunning = false;
                    System.exit(1); // 非0状态码表示异常退出
                }
            } catch (Exception e) {
                System.err.println("[Heartbeat] Error: " + e.getMessage());
                isServerRunning = false;
                System.exit(2);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private static AppMeta tryStartServer(List<VirtualMachineDescriptor> vmList, BufferedReader br, String starterJar, int port, String attachJar) throws IOException, AttachNotSupportedException, AgentLoadException, AgentInitializationException {
        String pid = System.getProperty("testkit.dig.pid", null);
        Optional<VirtualMachineDescriptor> chooseVm = vmList.stream().filter(new Predicate<VirtualMachineDescriptor>() {
            @Override
            public boolean test(VirtualMachineDescriptor virtualMachineDescriptor) {
                return pid != null && String.valueOf(virtualMachineDescriptor.id()).equals(pid.trim());
            }
        }).findFirst();
        VirtualMachineDescriptor targetVm;
        if (chooseVm.isPresent()) {
            System.out.println("Automatically select the preset pid:" + pid);
            targetVm = chooseVm.get();
        } else {
            for (int i = 0; i < vmList.size(); i++) {
                VirtualMachineDescriptor vmd = vmList.get(i);
                System.out.printf("%2d. %6s %s\n",
                        i + 1, vmd.id(), vmd.displayName());
            }
            // 用户选择进程
            int tryTimes = 0;
            do {
                tryTimes += 1;
                System.out.println(GREEN + "Please select Jvm:" + RESET);
                String lineNumber = br.readLine();
                if (EXIT.equalsIgnoreCase(lineNumber)) {
                    throw new IllegalArgumentException("Bye~");
                }
                try {
                    int choice = Integer.parseInt(lineNumber);
                    targetVm = vmList.get(choice - 1);
                    break;
                } catch (Throwable e) {
                    if (tryTimes >= 3) {
                        throw new IllegalArgumentException("Three consecutive selection failures! Bye~");
                    } else {
                        System.out.print(GREEN + "Your choice is wrong! Please re-select: " + RESET);
                    }
                }
            } while (true);
        }

        // 附加到目标JVM
        VirtualMachine vm = VirtualMachine.attach(targetVm);

        String ctx = System.getProperty("testkit.dig.ctx", null);
        if (ctx == null) {
            int tryTimes = 0;
            while (true) {
                tryTimes += 1;
                System.out.print(GREEN + "Enter a static attribute of type applicationContext（eg: com.hook.SpringContextUtil#context）: " + RESET);
                String input = br.readLine().trim();
                if (EXIT.equalsIgnoreCase(input)) {
                    throw new IllegalArgumentException("Bye~");
                }
                if (!input.isEmpty() && input.split("#").length == 2) {
                    ctx = input;
                    break;
                }
                if (tryTimes >= 5) {
                    throw new IllegalArgumentException("ctx failed to be entered for five times! Bye~");
                } else {
                    System.out.print(GREEN + "Please enter the correct ctx（eg: com.hook.SpringContextUtil#context）: " + RESET);
                }
            }
        }


        String env = null;
        String envKey = System.getProperty("testkit.dig.envKey", null);
        if (envKey == null) {
            int tryTimes = 0;
            while (true) {
                tryTimes += 1;
                System.out.print(GREEN + "Please enter the current environment（empty means that env is null and cannot be local）: " + RESET);
                String input = br.readLine().trim();
                if (input.isEmpty() || !input.trim().equals("local")) {
                    env = input.trim();
                    if (env.equals("null") || env.isEmpty()) {
                        env = null;
                    }
                    break;
                }
                if (tryTimes >= 5) {
                    throw new IllegalArgumentException("env failed to be entered for five times！Bye~");
                } else {
                    System.out.print(GREEN + "Please enter the correct ctx（empty means that env is null and cannot be local）: " + RESET);
                }
            }
        }

        HashMap<String, Object> map = new HashMap<>();
        map.put("starter", starterJar);
        map.put("ctx", ctx);
        map.put("port", port);
        String logPath = System.getProperty("java.io.tmpdir") + File.separator + "testkit-dig.txt";
        map.put("log_path", logPath);
        if (envKey != null) {
            map.put("envKey", envKey);
        } else {
            map.put("env", (env == null ? "" : env));
        }

        String agentParams = encode(map);
        try {
            vm.loadAgent(attachJar, agentParams);
        } catch (AgentLoadException | IOException e) {
            System.out.println("Agent load fail, " + e.getMessage());
            throw new RuntimeException(e);
        } catch (AgentInitializationException e) {
            System.out.println("Agent init fail, code: " + e.returnValue() + ", JVM-Version：" + vm.getSystemProperties().getProperty("java.version"));
            throw new RuntimeException(e);
        }

        long millis = System.currentTimeMillis();
        System.out.println("Wait for connection:" + agentParams);
        //发起http请求等待链接成功
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            AppMeta runningApp = findRunningApp(port);
            if (runningApp != null) {
                return runningApp;
            }
            if (System.currentTimeMillis() - millis > 5_000) {
                // 一次性读取全部字节后转字符串（适合小文件）
                byte[] bytes = Files.readAllBytes(Paths.get(logPath).normalize());
                String log = new String(bytes, StandardCharsets.UTF_8);
                if (log != null) {
                    throw new RuntimeException("Agent load fail\n" + log);
                } else {
                    throw new RuntimeException("Wait timeout, Bye~");
                }
            }
        }
    }

    private static void printJvmList(List<VirtualMachineDescriptor> list) {

    }


    private static void startCommandLoop(BufferedReader br, int port) throws IOException {
        // 启动心跳检测
        startHeartbeatCheck(port);
        try {
            while (isServerRunning) {
//                System.out.print("testkit-dig> ");
                System.out.print(GREEN + "testkit-dig> " + RESET);
                String cmd = br.readLine().trim();
                if ("exit".equalsIgnoreCase(cmd)) {
                    break;
                }
                // 这里需要实现命令分发
                try {
                    String ret = processCommand(cmd, port);
                    System.out.println(ret);
                } catch (Exception e) {
                    System.out.println(RED + e.getMessage() + RESET);
                }
            }
        } finally {
            shutdownGracefully();
        }
    }

    private static String processCommand(String cmd, int port) throws Exception {
        // 示例命令处理
        if (cmd.equalsIgnoreCase("hello")) {
            return YELLOW + "[hello] " + JSON.toJSONString(findRunningApp(port)) + RESET;
            // 调用Agent通信接口
        } else if (cmd.equalsIgnoreCase("stop")) {
            HashMap<String, String> requestData = new HashMap<>();
            requestData.put("method", "stop");
            try {
                JSONObject response = HttpUtil.sendPost("http://localhost:" + port + "/", requestData, JSONObject.class);
                if (!response.getBooleanValue("success")) {
                    return YELLOW + "[stop] " + response.getString("message") + RESET;
                }
                return YELLOW + "[stop] success" + RESET;
            } catch (Exception e) {
                return RED + "[stop] " + e.getMessage() + RESET;
            }
        } else if (cmd.startsWith("function-call ")) {
            String req = cmd.substring("function-call ".length());
            JSONObject reqObject = JSON.parseObject(req);
            return YELLOW + "[function-call]" + RESET + submitReqAndWaitRet(port, reqObject);
        } else if (cmd.startsWith("flexible-test ")) {
            String req = cmd.substring("flexible-test ".length());
            JSONObject reqObject = JSON.parseObject(req);
            return YELLOW + "[flexible-test]" + RESET + submitReqAndWaitRet(port, reqObject);
        } else if (cmd.startsWith("spring-cache ")) {
            String req = cmd.substring("spring-cache ".length());
            JSONObject reqObject = JSON.parseObject(req);
            return YELLOW + "[spring-cache]" + RESET + submitReqAndWaitRet(port, reqObject);
        } else if (cmd.startsWith("view ")) {
            String req = cmd.substring("view ".length());
            String[] split = req.split("#");
            if (split.length != 2) {
                throw new IllegalArgumentException("view params must be like className#fieldName");
            }
            JSONObject submitRequest = new JSONObject();
            submitRequest.put("method", "view-value");
            submitRequest.put("trace", false);
            JSONObject value = new JSONObject();
            value.put("typeClass", split[0]);
            value.put("beanName", null);
            value.put("fieldName", split[1]);
            submitRequest.put("params", value);
            return YELLOW + "[view]" + RESET + submitReqAndWaitRet(port, submitRequest);
        }
        return RED + "unknown Cmd: " + cmd + RESET;
    }

    private static String submitReqAndWaitRet(int port, JSONObject reqObject) throws Exception {
        JSONObject response = HttpUtil.sendPost("http://localhost:" + port + "/", reqObject, JSONObject.class);
        if (response == null || !response.getBooleanValue("success") || response.getString("data") == null) {
            return RED + "submit req error \n" + response.getString("message") + RESET;
        }
        String reqId = response.getString("data");

        HashMap<String, Object> map = new HashMap<>();
        map.put("method", "get_task_ret");
        HashMap<Object, Object> params = new HashMap<>();
        params.put("reqId", reqId);
        map.put("params", params);

        JSONObject result = HttpUtil.sendPost("http://localhost:" + port + "/", map, JSONObject.class);
        if (result == null) {
            return RED + "req is error\n result is null" + RESET;
        } else {
            if (!result.getBooleanValue("success")) {
                return GREEN + "[cost:" + result.get("cost") + "]" + RESET + "\nreq is error\n" + RED + result.getString("message") + RESET;
            } else {
                Object data = result.get("data");
                if (data == null) {
                    return GREEN + "[cost:" + result.get("cost") + "]" + RESET + "\nnull";
                } else if (data instanceof String
                        || data instanceof Byte
                        || data instanceof Short
                        || data instanceof Integer
                        || data instanceof Long
                        || data instanceof Float
                        || data instanceof Double
                        || data instanceof Character
                        || data instanceof Boolean
                        || data.getClass().isEnum()) {
                    return GREEN + "[cost:" + result.get("cost") + "]" + RESET + "\n" + data;
                } else {
                    return GREEN + "[cost:" + result.get("cost") + "]" + RESET + "\n" + JSON.toJSONString(data, SerializerFeature.PrettyFormat);
                }
            }
        }
    }

    // 安全关闭资源
    private static void shutdownGracefully() {
        try {
            heartbeatExecutor.shutdownNow();
            if (!heartbeatExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                System.err.println("Heartbeat executor did not terminate");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    public static File getEmbeddedJar(String jarName) throws IOException {
        URL resourceUrl = TestkitDigGuide.class.getClassLoader().getResource(LIB_DIR + jarName);
        if (resourceUrl == null) {
            throw new FileNotFoundException("Embedded JAR not found: " + jarName);
        }

        // 创建临时文件（自动清理）
        Path tempFile = Files.createTempFile("testkit-", ".jar");
        tempFile.toFile().deleteOnExit();

        try (InputStream in = resourceUrl.openStream()) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile.toFile();
    }


    // 编码：Map → URL参数字符串
    public static String encode(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            String key1 = entry.getKey();
            Object value1 = entry.getValue();
            if (key1 == null || value1 == null) {
                continue;
            }

            String key = encodeComponent(key1);
            String value = encodeComponent(value1.toString());
            sb.append(key).append("=").append(value);
        }
        return sb.toString();
    }

    // URL组件编码（兼容空格处理）
    private static String encodeComponent(String s) {
        if (s == null) {
            return "";
        }
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name())
                    .replace("+", "%20"); // 将+替换为%20
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
    }


    public static class AppMeta {

        private String ip;
        private boolean enableTrace;
        private String app;
        private String env;

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public boolean isEnableTrace() {
            return enableTrace;
        }

        public void setEnableTrace(boolean enableTrace) {
            this.enableTrace = enableTrace;
        }

        public String getApp() {
            return app;
        }

        public void setApp(String app) {
            this.app = app;
        }

        public String getEnv() {
            return env;
        }

        public void setEnv(String env) {
            this.env = env;
        }
    }
}