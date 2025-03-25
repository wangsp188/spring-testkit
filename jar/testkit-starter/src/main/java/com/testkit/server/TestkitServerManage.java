package com.testkit.server;

import org.springframework.context.ApplicationContext;

public class TestkitServerManage {

    private static TestkitServer server;

    public static boolean hasServer() {
        return server != null;
    }

    public static TestkitServer findServer() {
        return server;
    }


    public synchronized static TestkitServer createOrStartServer(ApplicationContext app, String project, String appName, String env, boolean enableTrace) {
        if (app == null) {
            throw new TestkitException("app can not be null");
        }
        TestkitServer exist = findServer();
        if (exist != null) {
            System.err.println("already has a testkit server, exist project:" + exist.getProject() + ", appName:" + exist.getAppName() + ", env:" + exist.getEnv() + ", enableTrace:" + exist.isEnableTrace());
            return exist;
        }
        TestkitServer tempServer = new TestkitServer(app, project, appName, env, enableTrace);
        tempServer.start();
        TestkitServerManage.server = tempServer;
        return tempServer;
    }


}