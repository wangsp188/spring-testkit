package com.testkit.server;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

public class TestkitServerManage {

    private static TestkitServer pluginServer;

    private static final Map<Integer, TestkitServer> CLIServers = new HashMap<>();

    public static boolean hasPluginServer() {
        return pluginServer != null;
    }

    public static TestkitServer findPluginServer() {
        return pluginServer;
    }

    public static boolean hasCLIServer(Integer port) {
        return CLIServers.get(port) != null;
    }

    public static TestkitServer findCLIServer(Integer port) {
        return CLIServers.get(port);
    }


    public synchronized static TestkitServer startPluginServer(ApplicationContext app, String project, String appName, String env, boolean enableTrace) {
        if (app == null) {
            throw new TestkitException("app can not be null");
        }
        TestkitServer exist = findPluginServer();
        if (exist != null) {
            System.err.println("already has a testkit server, exist project:" + exist.getProject() + ", appName:" + exist.getAppName() + ", env:" + exist.getEnv() + ", enableTrace:" + exist.isEnableTrace());
            return exist;
        }
        TestkitServer tempServer = new TestkitServer(app, project, appName, env, enableTrace);
        tempServer.start();
        return TestkitServerManage.pluginServer = tempServer;
    }


    public synchronized static TestkitServer startCLIServer(ApplicationContext app, String env, int port) {
        if (app == null) {
            throw new TestkitException("app can not be null");
        }
        TestkitServer exist = findCLIServer(port);
        if (exist != null) {
            System.err.println("already has a testkit server, exist project:" + exist.getProject() + ", appName:" + exist.getAppName() + ", env:" + exist.getEnv() + ", enableTrace:" + exist.isEnableTrace());
            if (!exist.isRunning()) {
                exist.start(port);
            }
            return exist;
        }
        TestkitServer tempServer = new TestkitServer(app, "remote-cli", getStartupClass(app), env, false);
        tempServer.start(port);
        return TestkitServerManage.CLIServers.put(port, tempServer);
    }


    public static String getStartupClass(ApplicationContext app) {
        // 查找带有@SpringBootApplication注解的Bean
        String[] beanNames = app.getBeanNamesForAnnotation(SpringBootApplication.class);
        if (beanNames.length > 0) {
            Object bean = app.getBean(beanNames[0]);
            String simpleName = ReflexUtils.getTargetObject(bean).getClass().getSimpleName();
            if (simpleName.contains("$$")) {
                return simpleName.substring(0, simpleName.indexOf("$$"));
            }
            return simpleName;
        }
        return null;
    }


}