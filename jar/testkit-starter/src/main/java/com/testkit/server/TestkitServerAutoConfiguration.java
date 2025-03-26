package com.testkit.server;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

public class TestkitServerAutoConfiguration implements DisposableBean {

    private TestkitServer server;


    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ApplicationContext app = event.getApplicationContext();
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
