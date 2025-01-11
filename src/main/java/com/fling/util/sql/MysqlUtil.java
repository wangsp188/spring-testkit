package com.fling.util.sql;

import com.fling.SettingsStorageHelper;

import java.sql.Connection;
import java.sql.DriverManager;

public class MysqlUtil {


    public static String testConnectionAndClose(SettingsStorageHelper.DatasourceConfig datasource) {
        Connection connection = null;
        try {
            // 尝试从 DataSource 获取连接
            connection = getDatabaseConnection(datasource);
            // 如果获取成功，返回 null 表示连接成功
            return null;
        } catch (Exception e) {
            // 捕获异常并返回异常信息
            return e.getMessage();
        } finally {
            // 确保连接在使用后关闭，避免资源泄漏
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    // 忽略关闭连接的异常
                }
            }
        }
    }

    public static Connection getDatabaseConnection(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
        if (datasourceConfig == null) {
            throw new IllegalArgumentException("Datasource config cannot be null");
        }
        // 加载驱动（如果未自动注册）
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            // 直接通过 DriverManager 获取连接
            return DriverManager.getConnection(
                    datasourceConfig.getUrl(),
                    datasourceConfig.getUsername(),
                    datasourceConfig.getPassword()
            );
        } catch (Throwable e) {
            throw new RuntimeException("Failed to connect", e);
        }
    }

}
