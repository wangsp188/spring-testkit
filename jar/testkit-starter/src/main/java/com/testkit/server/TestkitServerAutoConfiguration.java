package com.testkit.server;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

public class TestkitServerAutoConfiguration implements DisposableBean {

    private TestkitServer server;

    // Web 应用场景：Tomcat/Jetty/Undertow 启动后触发
    @EventListener(ServletWebServerInitializedEvent.class)
    public void onWebServerInitialized(WebServerInitializedEvent event) {
        System.err.println("Testkit onWebServerInitialized");
        startPluginServer(event.getApplicationContext());
    }

    // 非 Web 应用场景：ApplicationReadyEvent 后触发
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        System.err.println("Testkit onApplicationReady");
        startPluginServer(event.getApplicationContext());
    }

    private synchronized void  startPluginServer(ApplicationContext app) {
        if (this.server!=null) {
            return;
        }
        Environment environment = app.getEnvironment();
        String project = environment.getProperty("testkit.project.name");
        String appName = environment.getProperty("testkit.app.name");
        String env = environment.getProperty("testkit.app.env");
        boolean enableTrace = "true".equals(environment.getProperty("testkit.trace.enable"));
        this.server = TestkitServerManage.startPluginServer(app, project, appName, env, enableTrace);
    }

    @Override
    public void destroy() {
        if (this.server == null) {
            return;
        }
        this.server.stop();
    }
}
