package com.testkit.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestkitServerManage {

    private static TestkitServer server;

    public static TestkitServer findServer() {
        return server;
    }

    public synchronized static TestkitServer startServer(ApplicationContext app, String project, String appName, String env, boolean enableTrace) {
        if (app == null) {
            throw new TestkitException("app can not be null");
        }
        TestkitServer exist = findServer();
        if (exist != null) {
            System.err.println("already has a testkit server, exist project:" + exist.getProject() + ", appName:" + exist.getAppName() + ", env:" + exist.getEnv() + ", enableTrace:" + exist.isEnableTrace());
            if (!exist.isRunning()) {
                exist.start();
            }
            return exist;
        }
        TestkitServer tempServer = new TestkitServer(app, project, appName, env, enableTrace);
        tempServer.start();
        return TestkitServerManage.server = tempServer;
    }


    public synchronized static TestkitServer startCLIServer(ApplicationContext app, String env, String pidPath) {
        TestkitServer testkitServer = startServer(app, RuntimeAppHelper.TESTKIT_CLI_PROJECT, getStartupClass(app), env, false);
        Integer port = testkitServer.fetchServerPort();
        if (port == null) {
            throw new RuntimeException("no server started");
        }
        if (pidPath != null && !pidPath.isEmpty()) {
            //向String pidPath;写入内容 port
            Path path = Paths.get(pidPath);
            try {
                // 将端口号写入文件（覆盖模式）
                Files.write(path, String.valueOf(port).getBytes(StandardCharsets.UTF_8));
            } catch (Throwable e) {
                throw new RuntimeException("fail write port to " + pidPath, e);
            }
        }
        return testkitServer;
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