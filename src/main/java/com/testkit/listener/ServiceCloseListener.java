package com.testkit.listener;

import com.intellij.ide.AppLifecycleListener;
import com.testkit.tools.mcp_function.McpHelper;
import io.modelcontextprotocol.client.McpSyncClient;
import org.apache.commons.collections.MapUtils;

import java.util.Map;

public class ServiceCloseListener implements AppLifecycleListener {

    @Override
    public void appWillBeClosed(boolean isRestart) {
        System.err.println("close MCP-services");
        Map<String, McpSyncClient> clients = McpHelper.getClients();
        if (MapUtils.isEmpty(clients)) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<String, McpSyncClient> clientEntry : clients.entrySet()) {
                    System.err.println("close MCP-server, "+clientEntry.getKey());
                    clientEntry.getValue().close();
                }
            }
        }).start();
    }
}