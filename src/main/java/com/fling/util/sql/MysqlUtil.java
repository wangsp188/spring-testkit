package com.fling.util.sql;

import com.alibaba.druid.pool.DruidDataSource;
import com.fling.SettingsStorageHelper;

import javax.sql.DataSource;
import java.sql.Connection;

public class MysqlUtil {


    public static String testConnectionAndClose(DruidDataSource datasource) {
        Connection connection = null;

        try {
            // 尝试从 DataSource 获取连接
            connection = datasource.getConnection();

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
            datasource.close();
        }
    }




    public static DruidDataSource getDruidDataSource(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
        if (datasourceConfig==null) {
            return null;
        }
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setInitialSize(0); // 初始化连接数
        dataSource.setMinIdle(0);     // 最小空闲连接数
        dataSource.setMaxActive(1);
        dataSource.setMaxWait(10000);
        dataSource.setUrl(datasourceConfig.getUrl());
        dataSource.setUsername(datasourceConfig.getUsername());
        dataSource.setPassword(datasourceConfig.getPassword());
        return dataSource;
    }

}
