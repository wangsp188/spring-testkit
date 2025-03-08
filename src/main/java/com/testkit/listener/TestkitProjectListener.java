package com.testkit.listener;

import com.alibaba.fastjson.JSON;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.TestkitHelper;
import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.testkit.sql_review.MysqlUtil;
import com.testkit.view.TestkitToolWindow;
import com.testkit.view.TestkitToolWindowFactory;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TestkitProjectListener implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        DumbService.getInstance(project).smartInvokeLater(() -> {
            try {
                new Thread(() -> {
                    while (true) {
                        CodingGuidelinesHelper.refreshDoc(project);
                        TestkitHelper.refresh(project);
                        //刷新数据库链接
//                        refreshValidDatasources(project);
                        try {
                            Thread.sleep(24 * 3600 * 1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }).start();
            } catch (Exception e) {
                TestkitHelper.notify(project, NotificationType.ERROR, "Schedule refresh failed," + e.getClass().getSimpleName() + ", " + e.getMessage());
            }
        });
        return null;
    }

    private static void refreshValidDatasources(Project project) {
        try {
            String propertiesStr = SettingsStorageHelper.getSqlConfig(project).getProperties();
            if (StringUtils.isBlank(propertiesStr) || Objects.equals(propertiesStr, SettingsStorageHelper.datasourceTemplateProperties)) {
                return;
            }
            TestkitToolWindow toolWindow = TestkitToolWindowFactory.getToolWindow(project);
            if (toolWindow==null) {
                return;
            }

            List<SettingsStorageHelper.DatasourceConfig> datasources = RuntimeHelper.getValidDatasources(project.getName());

            List<SettingsStorageHelper.DatasourceConfig> sqlConfig = SettingsStorageHelper.SqlConfig.parseDatasources(propertiesStr);

            Set<String> oldValidNames = datasources.stream().map(new Function<SettingsStorageHelper.DatasourceConfig, String>() {
                @Override
                public String apply(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
                    return datasourceConfig.getName();
                }
            }).collect(Collectors.toSet());
            Set<String> configNames = sqlConfig.stream().map(new Function<SettingsStorageHelper.DatasourceConfig, String>() {
                @Override
                public String apply(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
                    return datasourceConfig.getName();
                }
            }).collect(Collectors.toSet());
            //有无效的就重新判断下
            if (oldValidNames.containsAll(configNames)) {
                return;
            }
            Map<String, String> results = new HashMap<>();
            List<SettingsStorageHelper.DatasourceConfig> valids = new ArrayList<>();
            List<String> ddls = new ArrayList<>();
            List<String> writes = new ArrayList<>();
            for (SettingsStorageHelper.DatasourceConfig config : sqlConfig) {
                // 测试连接
                Object result = MysqlUtil.testConnectionAndClose(config);
                if (result instanceof Integer) {
                    valids.add(config);
                    if (Objects.equals(result, 2)) {
                        ddls.add(config.getName());
                    } else if (Objects.equals(result, 1)) {
                        writes.add(config.getName());
                    }
                }
                results.put(config.getName(), !(result instanceof String) ? ("connect success, DDL: " + result) : result.toString());
            }

            Set<String> validNames = valids.stream().map(new Function<SettingsStorageHelper.DatasourceConfig, String>() {
                @Override
                public String apply(SettingsStorageHelper.DatasourceConfig datasourceConfig) {
                    return datasourceConfig.getName();
                }
            }).collect(Collectors.toSet());

            Set<String> oldDdls = new HashSet<>(RuntimeHelper.getDDLDatasources(project.getName()));
            Set<String> oldWrites = new HashSet<>(RuntimeHelper.getWriteDatasources(project.getName()));
            if(oldDdls.containsAll(ddls) && new HashSet<>(ddls).containsAll(oldDdls)
            && new HashSet<>(writes).containsAll(oldWrites) && oldWrites.containsAll(writes)
                    && oldValidNames.containsAll(validNames) && validNames.containsAll(oldValidNames)
            ) {
                return;
            }
            RuntimeHelper.updateValidDatasources(project.getName(), valids, ddls, writes);
            //更新
            toolWindow.refreshSqlDatasources();
            TestkitHelper.notify(project, NotificationType.INFORMATION, "SQL Tool is updated, Connection result is " + JSON.toJSONString(results));
        } catch (Throwable e) {
            System.out.println("datasource更新失败" + e.getMessage());
        }
    }
}