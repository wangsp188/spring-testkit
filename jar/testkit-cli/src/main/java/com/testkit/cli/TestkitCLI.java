package com.testkit.cli;

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class TestkitCLI {

    private static final String RESET = "\033[0m";
    private static final String GREEN = "\033[1;32m";  // 亮绿色
    private static final String YELLOW = "\033[1;33m"; // 亮黄色
    private static final String RED = "\033[1;31m";    // 亮红色

    private static final String LIB_DIR = "lib/";
    public static final String EXIT = "exit";
    public static final String TESTKIT_CLI_ATTACH_1_0_JAR = "testkit-cli-attach-1.0.jar";
    public static final String TESTKIT_STARTER_1_0_JAR = "testkit-starter-1.0.jar";
    private static volatile boolean isServerRunning = true;
    private static final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();


    public static void main(String[] args) throws Exception {
        // 修改输出流编码
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8.name());
        System.setOut(out);
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String ctxProperty = System.getProperty("testkit.cli.ctx", null);
        if (ctxProperty != null && ctxProperty.split("#").length != 2) {
            throw new IllegalArgumentException("testkit.cli.ctx must like clsName#fieldName");
        }
        System.out.println(RED + "---------------Do not use online server, only technical learning---------------" + RESET);
        System.out.println(GREEN + "  _________            .__                 ___________              __   __   .__  __   \n" +
                " /   _____/____________|__| ____    ____   \\__    ___/___   _______/  |_|  | _|__|/  |_ \n" +
                " \\_____  \\\\____ \\_  __ \\  |/    \\  / ___\\    |    |_/ __ \\ /  ___/\\   __\\  |/ /  \\   __\\\n" +
                " /        \\  |_> >  | \\/  |   |  \\/ /_/  >   |    |\\  ___/ \\___ \\  |  | |    <|  ||  |  \n" +
                "/_______  /   __/|__|  |__|___|  /\\___  /    |____| \\___  >____  > |__| |__|_ \\__||__|  \n" +
                "        \\/|__|                 \\//_____/                \\/     \\/            \\/         " + RESET);
        System.out.println(RED + "---------------Do not use online server, only technical learning---------------" + RESET);

        AtomicReference<String> ctxAtc = new AtomicReference<>(ctxProperty);
        // 加载嵌入的JAR文件
        File attachJar = getEmbeddedJar(TESTKIT_CLI_ATTACH_1_0_JAR);
        File starterJar = getEmbeddedJar(TESTKIT_STARTER_1_0_JAR);
        attachJar.deleteOnExit();
        starterJar.deleteOnExit();
        // 列出所有JVM进程4
        List<VirtualMachineDescriptor> vmList = VirtualMachine.list();
        vmList = vmList.stream().filter(new Predicate<VirtualMachineDescriptor>() {
            @Override
            public boolean test(VirtualMachineDescriptor virtualMachineDescriptor) {
                String displayName = virtualMachineDescriptor.displayName();
                return !displayName.contains(TestkitCLI.class.getName())
                        && !displayName.contains("IntelliJ IDEA.app")
                        && !displayName.contains("com.intellij.idea.Main")
                        && !displayName.contains("testkit-cli-1.0.jar")
                        ;
            }
        }).toList();
        if (vmList.isEmpty()) {
            System.out.println("No virtual machine found");
            return;
        }

        AppMeta runningApp = startServerGuide(vmList, br, starterJar.getAbsolutePath(), attachJar.getAbsolutePath(), ctxAtc);
        System.out.println(GREEN + "Connect success" + RESET);
        System.out.println(YELLOW + "You can manually create a connector in plugin panel using the following information:" + RESET);
        System.out.println(RED + "App: " + runningApp.getApp() + RESET);
        System.out.println(RED + "Ip: " + runningApp.getIp() + RESET);
        System.out.println(RED + "Testkit port: " + runningApp.getPort() + RESET);
        // 启动交互式命令行
        startCommandLoop(br, "localhost",runningApp.getPort(), ctxAtc.get());
    }


    private static AppMeta findRunningApp(String host, int port, String ctx) {
        JSONObject requestData = new JSONObject();
        requestData.put("method", "hi");
        if (ctx != null) {
            HashMap<Object, Object> map = new HashMap<>();
            map.put("cls", ctx.split("#")[0]);
            requestData.put("params", map);
        }

        try {
            JSONObject response = HttpUtil.sendPost("http://"+host+":" + port + "/", requestData, JSONObject.class,5,5);
            if (!response.getBooleanValue("success")) {
                return null;
            }
            return response.getObject("data", AppMeta.class);
        } catch (Exception e) {
            //判断
            if (!HttpUtil.isTcpPortAvailable(port)) {
                throw new IllegalArgumentException("The port:"+port+" is occupied.");
            }
            return null;
        }
    }

    private static StatusMsg directRequest(String host, int port, Map<String, Object> requestData) throws Exception {
        JSONObject result = HttpUtil.sendPost("http://"+host+":" + port + "/", requestData, JSONObject.class,5,30);
        if (result == null) {
            return new StatusMsg(false,RED + "req is error\n result is null" + RESET);
        }
        if (!result.getBooleanValue("success")) {
            return new StatusMsg(false,GREEN + "[cost:" + result.get("cost") + "]" + RESET + "\nreq is error\n" + RED + result.getString("message") + RESET);
        }
        Object data = result.get("data");
        if (data == null) {
            return new StatusMsg(true,GREEN + "[cost:" + result.get("cost") + "]" + RESET + "\nnull");
        }
        if (data instanceof String
                || data instanceof Byte
                || data instanceof Short
                || data instanceof Integer
                || data instanceof Long
                || data instanceof Float
                || data instanceof Double
                || data instanceof Character
                || data instanceof Boolean
                || data.getClass().isEnum()) {
            return new StatusMsg(true,GREEN + "[cost:" + result.get("cost") + "]" + RESET + "\n" + data);
        } else {
            return new StatusMsg(true,GREEN + "[cost:" + result.get("cost") + "]" + RESET + "\n" + JSON.toJSONString(data, SerializerFeature.PrettyFormat));
        }
    }

    private static void startHeartbeatCheck(String host,int port, String ctx) {
        heartbeatExecutor.scheduleWithFixedDelay(() -> {
            try {
                AppMeta app = findRunningApp(host,port, ctx);
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
        }, 0, 3, TimeUnit.SECONDS);
    }

    private static AppMeta startServerGuide(List<VirtualMachineDescriptor> vmList, BufferedReader br, String starterJar, String attachJar, AtomicReference<String> textCtx) throws IOException, AttachNotSupportedException, AgentLoadException, AgentInitializationException {
        String pid = System.getProperty("testkit.cli.pid", null);
        Optional<VirtualMachineDescriptor> chooseVm = vmList.stream().filter(new Predicate<VirtualMachineDescriptor>() {
            @Override
            public boolean test(VirtualMachineDescriptor virtualMachineDescriptor) {
                return pid != null && pid.trim().equals(virtualMachineDescriptor.id());
            }
        }).findFirst();
        VirtualMachineDescriptor targetVm = null;
        if (chooseVm.isPresent()) {
            System.out.println("Automatically select the preset pid:" + pid);
            targetVm = chooseVm.get();
        } else {
            System.out.printf(YELLOW+"%2d. %6s %s\n",
                    0, 0, "Remote connection"+RESET);
            for (int i = 0; i < vmList.size(); i++) {
                VirtualMachineDescriptor vmd = vmList.get(i);
                System.out.printf("%2d. %6s %s\n",
                        i + 1, vmd.id(), vmd.displayName());
            }
            // 用户选择进程
            int tryTimes = 0;
            do {
                tryTimes += 1;
                System.out.print(GREEN + "Please select Jvm(0 represents a remote connection): " + RESET);
                String lineNumber = br.readLine();
                if (EXIT.equalsIgnoreCase(lineNumber)) {
                    throw new IllegalArgumentException("Bye~");
                }
                try {
                    int choice = Integer.parseInt(lineNumber);
                    if (choice == 0) {
                        break;
                    }
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
        //remote 链接
        if (targetVm == null) {
            System.out.print(GREEN + "Please enter remote host (IP or domain, empty is equals to localhost): " + RESET);
            String host = br.readLine().trim();
            if (EXIT.equalsIgnoreCase(host)) {
                throw new IllegalArgumentException("Bye~");
            }
            host = host.trim().isEmpty() ? "localhost" : host.trim().toLowerCase();

            int port = 0;
            int tryTimes = 0;
            while (port <= 0) {
                tryTimes++;
                System.out.print(GREEN + "Please enter remote port: " + RESET);
                String portStr = br.readLine().trim();
                if (EXIT.equalsIgnoreCase(portStr)) {
                    throw new IllegalArgumentException("Bye~");
                }
                try {
                    port = Integer.parseInt(portStr);
                    if (port <= 0 || port > 65535) {
                        System.out.println(RED + "Port must be between 1 and 65535." + RESET);
                        port = 0;
                    }
                } catch (NumberFormatException e) {
                    System.out.println(RED + "Invalid port number." + RESET);
                }
                if (tryTimes >= 3) {
                    throw new IllegalArgumentException("Three consecutive input failures! Bye~");
                }
            }
            AppMeta runningApp = findRunningApp(host, port, null);
            if (runningApp == null) {
                throw new IllegalArgumentException("Connection fail, Bye~");
            }
            return runningApp;
        }

        // 附加到目标JVM
        VirtualMachine vm = VirtualMachine.attach(targetVm);
        // 获取用户主目录
        String userHome = System.getProperty("user.home");
        // 构建文件路径
        String pidPath = Paths.get(userHome, ".testkitCLI." + targetVm.id() + ".txt").toFile().getAbsolutePath();

        String ctx = System.getProperty("testkit.cli.ctx", null);
        if (ctx == null) {
            int tryTimes = 0;
            while (true) {
                tryTimes += 1;
                System.out.print(GREEN + "Enter a static attribute of type applicationContext (eg: com.hook.SpringContextUtil#context): " + RESET);
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
                    System.out.print(GREEN + "Please enter the correct ctx (eg: com.hook.SpringContextUtil#context): " + RESET);
                }
            }
        }


        String envKey = System.getProperty("testkit.cli.env-key", null);
        if (envKey == null) {
            System.out.print(GREEN + "[Optional] Please enter the property key for the deploy environment in the target JVM: " + RESET);
            String input = br.readLine().trim();
            if (EXIT.equalsIgnoreCase(input)) {
                throw new IllegalArgumentException("Bye~");
            }
            envKey = input;
            if (envKey.isEmpty()) {
                System.out.print(YELLOW + "By default, spring.profiles.active will be used as the env-key" + RESET);
                envKey = "spring.profiles.active";
            }
        }

        textCtx.set(ctx);
        HashMap<String, Object> map = new HashMap<>();
        map.put("starter", starterJar);
        map.put("ctx", ctx);
        map.put("pid-path", pidPath);
        String logPath = System.getProperty("java.io.tmpdir") + File.separator + "testkit-cli.txt";
        map.put("log-path", logPath);
        map.put("env-key", envKey);
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
        System.out.println("attach log:" + logPath);
        //发起http请求等待链接成功
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }

            if (System.currentTimeMillis() - millis > 5_000) {
                // 一次性读取全部字节后转字符串（适合小文件）
                byte[] bytes = Files.readAllBytes(Paths.get(logPath).normalize());
                String log = new String(bytes, StandardCharsets.UTF_8);
                if (log != null) {
                    throw new RuntimeException("Agent load fail\n" + log);
                } else {
                    throw new RuntimeException("Wait timeout.");
                }
            }

            File file = new File(pidPath);
            if (!file.exists()) {
                continue;
            }
            int port = 0;
            try {
                port = Integer.parseInt(Files.readString(Path.of(pidPath)).trim());
            } catch (Throwable e) {
                throw new RuntimeException(pidPath + " content is not port");
            }
            AppMeta runningApp = findRunningApp("localhost", port, ctx);
            if (runningApp != null) {
                if (runningApp.isTestPass()) {
                    return runningApp;
                }
                throw new RuntimeException("ctx Verification fail on current service, " + JSON.toJSONString(runningApp));
            }
        }
    }


    private static void startCommandLoop(BufferedReader br, String host,int port, String ctx) throws IOException {
        // 启动心跳检测
        startHeartbeatCheck(host,port, ctx);
        try {
            AtomicReference<String> confirmCmd = new AtomicReference<>();
            while (isServerRunning) {
                System.out.print(GREEN + "testkit-cli> " + RESET);
                String cmd = br.readLine().trim();
                // 处理确认输入
                if (confirmCmd.get() != null) {
                    if (cmd.equalsIgnoreCase("Y")) {
                        try {
                            String ret = processCommand(confirmCmd.get(),host, port, false, confirmCmd);
                            System.out.println(ret);
                        } catch (Exception e) {
                            System.out.println(RED + e.getMessage() + RESET);
                        } finally {
                            confirmCmd.set(null);
                        }
                    } else {
                        confirmCmd.set(null);
                        System.out.println(YELLOW + "Operation cancel" + RESET);
                    }
                    continue;
                }
                confirmCmd.set(null);
                if (EXIT.equalsIgnoreCase(cmd)) {
                    break;
                }
                if (cmd.equalsIgnoreCase("hi")) {
                    System.out.println(YELLOW + "[hi] " + findRunningApp(host, port, ctx) + RESET);
                    continue;
                }
                // 示例命令处理
                if (cmd.equalsIgnoreCase("stop")) {
                    HashMap<String, String> requestData = new HashMap<>();
                    requestData.put("method", "stop");
                    try {
                        JSONObject response = HttpUtil.sendPost("http://"+host+":" + port + "/", requestData, JSONObject.class,5,30);
                        if (!response.getBooleanValue("success")) {
                            System.out.println(YELLOW + "[stop] " + response.getString("message") + RESET);
                            continue;
                        }
                        System.out.println(YELLOW + "[stop] success" + RESET);
                    } catch (Exception e) {
                        System.out.println(RED + "[stop] " + e.getMessage() + RESET);
                    }
                    continue;
                }
                if (cmd.startsWith("view ")) {
                    String req = cmd.substring("view ".length());
                    String[] split = req.split("#");
                    if (split.length == 2) {
                        JSONObject submitRequest = new JSONObject();
                        submitRequest.put("method", "view-value");
                        submitRequest.put("trace", false);
                        JSONObject value = new JSONObject();
                        value.put("typeClass", split[0]);
                        value.put("beanName", null);
                        value.put("fieldName", split[1]);
                        submitRequest.put("params", value);
                        try {
                            System.out.println(YELLOW + "[view-field]" + RESET + submitReqAndWaitRet(host,port, submitRequest));
                        } catch (Exception e) {
                            System.out.println(RED + e.getMessage() + RESET);
                        }
                        continue;
                    }
                    JSONObject submitRequest = new JSONObject();
                    submitRequest.put("method", "property");
                    JSONObject value = new JSONObject();
                    value.put("property", req);
                    submitRequest.put("params", value);
                    try {
                        System.out.println(YELLOW + "[view-property]" + RESET + directRequest(host,port, submitRequest).getMessage());
                    } catch (Exception e) {
                        System.out.println(RED + e.getMessage() + RESET);
                    }
                    continue;
                }
                if (cmd.startsWith("debug ")) {
                    String bol = cmd.substring("debug ".length());
                    boolean debug = Objects.equals(bol, "true");
                    System.setProperty("testkit.cli.debug", String.valueOf(debug));
                    System.out.println(YELLOW + "[debug] " + System.getProperty("testkit.cli.debug") + RESET);
                    continue;
                }


                if (!cmd.startsWith("function-call ") && !cmd.startsWith("flexible-test ") && !cmd.startsWith("spring-cache ")) {
                    System.out.println(RED + "Unknown Cmd: " + cmd + RESET);
                    continue;
                }


                //这时数据可能是多行的，一直读到指定结尾是<<END>>的结束
                while (!cmd.endsWith("<<END>>")) {
                    String line = br.readLine().trim();
                    if (line.equals(";")) {
                        break;
                    }
                    cmd += (line.trim());
                }
                cmd = cmd.substring(0, cmd.length() - 7).trim();
                String debug = System.getProperty("testkit.cli.debug");
                if (Objects.equals("true", debug)) {
                    System.out.println();
                    System.out.println(GREEN + "[cmd]" + cmd + RESET);
                    System.out.println();
                }
                // 这里需要实现命令分发
                try {
                    String ret = processCommand(cmd, host, port, true, confirmCmd);
                    System.out.println(ret);
                } catch (Exception e) {
                    confirmCmd.set(null);
                    System.out.println(RED + e.getMessage() + RESET);
                }
            }
        } finally {
            shutdownGracefully();
        }
    }

    private static String processCommand(String cmd, String host, int port, boolean prepare, AtomicReference<String> confirmCmd) throws Exception {
        if (cmd.startsWith("function-call ")) {
            String req = cmd.substring("function-call ".length());
            JSONObject reqObject = JSON.parseObject(req);
            if (prepare) {
                reqObject.put("prepare", true);
                StatusMsg statusMsg = directRequest(host, port, reqObject);
                if(statusMsg.isSuccess()){
                    confirmCmd.set(cmd);
                    return YELLOW + "[function-call]" + RESET + statusMsg.getMessage() + "\n" + GREEN + "Confirm execution? (Y/N): " + RESET;
                } else {
                    return YELLOW + "[function-call]" + RESET + statusMsg.getMessage() + RESET;
                }
            }
            return YELLOW + "[function-call]" + RESET + submitReqAndWaitRet(host, port, reqObject);
        }
        if (cmd.startsWith("flexible-test ")) {
            String req = cmd.substring("flexible-test ".length());
            JSONObject reqObject = JSON.parseObject(req);
            if (prepare) {
                reqObject.put("prepare", true);
                StatusMsg statusMsg = directRequest(host, port, reqObject);
                if(statusMsg.isSuccess()){
                    confirmCmd.set(cmd);
                    return YELLOW + "[flexible-test]" + RESET + statusMsg.getMessage() + "\n" + GREEN + "Confirm execution? (Y/N): " + RESET;
                } else {
                    return YELLOW + "[flexible-test]" + RESET + statusMsg.getMessage() + RESET;
                }
            }
            return YELLOW + "[flexible-test]" + RESET + submitReqAndWaitRet(host, port, reqObject);
        }
        if (cmd.startsWith("spring-cache ")) {
            String req = cmd.substring("spring-cache ".length());
            JSONObject reqObject = JSON.parseObject(req);
            if ("delete_cache".equals(reqObject.getJSONObject("params").getString("action")) && prepare) {
                reqObject.put("prepare", true);
                StatusMsg statusMsg = directRequest(host, port, reqObject);
                if(statusMsg.isSuccess()){
                    confirmCmd.set(cmd);
                    return YELLOW + "[spring-cache]" + RESET + statusMsg.getMessage() + "\n" + GREEN + "Confirm execution? (Y/N): " + RESET;
                } else {
                    return YELLOW + "[spring-cache]" + RESET + statusMsg.getMessage() + RESET;
                }
            }
            return YELLOW + "[spring-cache]" + RESET + submitReqAndWaitRet(host, port, reqObject);
        }
        return RED + "Unknown Cmd: " + cmd + RESET;
    }

    private static String submitReqAndWaitRet(String host, int port, JSONObject reqObject) throws Exception {
        JSONObject response = HttpUtil.sendPost("http://"+host+":" + port + "/", reqObject, JSONObject.class,5,30);
        if (response == null || !response.getBooleanValue("success") || response.getString("data") == null) {
            return RED + "submit req error \n" + response.getString("message") + RESET;
        }
        String reqId = response.getString("data");

        HashMap<String, Object> map = new HashMap<>();
        map.put("method", "get_task_ret");
        HashMap<Object, Object> params = new HashMap<>();
        params.put("reqId", reqId);
        map.put("params", params);

        JSONObject result = HttpUtil.sendPost("http://"+host+":" + port + "/", map, JSONObject.class,5,600);
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
        URL resourceUrl = TestkitCLI.class.getClassLoader().getResource(LIB_DIR + jarName);
        if (resourceUrl == null) {
            throw new FileNotFoundException("Embedded JAR not found: " + jarName);
        }

        // 创建 testkit 临时目录（自动递归创建）
        Path testkitDir = Paths.get(System.getProperty("java.io.tmpdir"), "testkit-cli");
        Files.createDirectories(testkitDir);  // 如果目录已存在则直接返回

        // 在 testkit 目录下创建临时文件（自动清理）
        Path tempFile = Files.createTempFile(testkitDir, "testkit-", ".jar");
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
        private int port;
        private String ip;
        private boolean enableTrace;
        private String app;
        private String env;
        private boolean testPass;
        private boolean loadByCli;

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

        public boolean isTestPass() {
            return testPass;
        }

        public void setTestPass(boolean testPass) {
            this.testPass = testPass;
        }

        public boolean isLoadByCli() {
            return loadByCli;
        }

        public void setLoadByCli(boolean loadByCli) {
            this.loadByCli = loadByCli;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        @Override
        public String toString() {
            return "App:" + app + ", Ip: " + ip + ", Testkit port: " + port + ", Env: " + env + ", EnableTrace: " + enableTrace + ", LoadByCli: " + loadByCli;
        }
    }

    public static class StatusMsg{
        private boolean success;
        private String message;

        public StatusMsg(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}