package com.testkit.dig;

import com.sun.tools.attach.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TestkitDigGuide {


    public static void main(String[] args) throws Exception {
        // 列出所有JVM进程
        List<VirtualMachineDescriptor> vmList = VirtualMachine.list();
        vmList = vmList.stream().filter(new Predicate<VirtualMachineDescriptor>() {
            @Override
            public boolean test(VirtualMachineDescriptor virtualMachineDescriptor) {
                return !virtualMachineDescriptor.displayName().contains(TestkitDigGuide.class.getName());
            }
        }).toList();
        if (vmList.isEmpty()) {
            System.out.println("No virtual machine found");
            return;
        }
        int port = 30999;
        String portPro = System.getProperty("testkit.dig.port");
        if (portPro != null) {
            try {
                port = Integer.parseInt(portPro);
            } catch (NumberFormatException e) {
                throw new RuntimeException("testkit.dig.port 必须是数字", e);
            }
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String pid = System.getProperty("testkit.dig.pid", null);
        Optional<VirtualMachineDescriptor> chooseVm = vmList.stream().filter(new Predicate<VirtualMachineDescriptor>() {
            @Override
            public boolean test(VirtualMachineDescriptor virtualMachineDescriptor) {
                return pid != null && String.valueOf(virtualMachineDescriptor.id()).equals(pid.trim());
            }
        }).findFirst();
        VirtualMachineDescriptor targetVm;
        if (chooseVm.isPresent()) {
            System.out.println("选择预设的pid:" + pid);
            targetVm = chooseVm.get();
        } else {
            printJvmList(vmList);
            // 用户选择进程
            int tryTimes = 0;
            do {
                tryTimes += 1;
                System.out.print("请选择要连接的JVM序号: ");
                String lineNumber = br.readLine();
                try {
                    int choice = Integer.parseInt(lineNumber);
                    targetVm = vmList.get(choice - 1);
                    break;
                } catch (Throwable e) {
                    if (tryTimes >= 3) {
                        throw new IllegalArgumentException("JVM连续三次选择失败！欢迎再来~");
                    } else {
                        System.out.print("您的选择有误！请重新选择: ");
                    }
                }
            } while (true);
        }

        // 附加到目标JVM
        VirtualMachine vm = VirtualMachine.attach(targetVm);
        System.out.println("成功连接到PID: " + targetVm.id());

        String attachJar = "/Users/wangshaopeng/code/spring-testkit/jar/testkit-dig-attach/target/testkit-dig-attach-1.0.jar";
        String starterJar = "/Users/wangshaopeng/code/spring-testkit/jar/testkit-starter/target/testkit-starter-1.0.jar";

        String ctx = System.getProperty("testkit.dig.ctx", null);
        if (ctx == null) {
            int tryTimes = 0;
            while (true) {
                tryTimes += 1;
                System.out.print("请输入类型是applicationContext的静态属性（例如com.hook.SpringContextUtil#context）: ");
                String input = br.readLine().trim();
                if (!input.isEmpty() && input.split("#").length == 2) {
                    ctx = input;
                    break;
                }
                if (tryTimes >= 5) {
                    throw new IllegalArgumentException("ctx连续五次输入失败！欢迎再来~");
                } else {
                    System.out.print("请输入正确内容（例如com.hook.SpringContextUtil#context）: ");
                }
            }
        }


        String env = null;
        String envKey = System.getProperty("testkit.dig.envKey", null);
        if (envKey == null) {
            int tryTimes = 0;
            while (true) {
                tryTimes += 1;
                System.out.print("请输入当前所属环境（null代表env为空，不可为local，此参数会传递给Tool-interceptor）: ");
                String input = br.readLine().trim();
                if (!input.isEmpty() && !input.trim().equals("local")) {
                    env = input.trim();
                    if (env.equals("null")) {
                        env = null;
                    }
                    break;
                }
                if (tryTimes >= 5) {
                    throw new IllegalArgumentException("env连续五次输入失败！欢迎再来~");
                } else {
                    System.out.print("请输入有效环境（null代表env为空，不可为local）: ");
                }
            }
        }

        String params = "starter=" + starterJar + "&ctx=" + ctx + "&port=" + port;
        if (envKey != null) {
            params += "&envKey=" + envKey;
        } else {
            params += "&env=" + (env == null ? "" : env);
        }
        vm.loadAgent(attachJar, params);

        System.out.println("等待连接:"+params);
        //发起http请求等待链接成功
        int tryTimes = 0;
        while (true) {
            tryTimes += 1;
            if(true){
                break;
            }
        }
        // 启动交互式命令行
        startCommandLoop(port);
    }

    private static void printJvmList(List<VirtualMachineDescriptor> list) {
        System.out.println("可用JVM进程:");
        for (int i = 0; i < list.size(); i++) {
            VirtualMachineDescriptor vmd = list.get(i);
            System.out.printf("%2d. %6s %s\n",
                    i + 1, vmd.id(), vmd.displayName());
        }
    }


    private static void startCommandLoop(int port) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("testkit-dig> ");
                String cmd = br.readLine().trim();
                if ("exit".equalsIgnoreCase(cmd)){
                    break;
                }
                // 这里需要实现命令分发
                processCommand(cmd);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processCommand(String cmd) {
        // 示例命令处理
        if (cmd.startsWith("connect")) {
            System.out.println("[INFO] 正在建立连接...");
            // 调用Agent通信接口
        } else {
            System.out.println("未知命令: " + cmd);
        }
    }
}