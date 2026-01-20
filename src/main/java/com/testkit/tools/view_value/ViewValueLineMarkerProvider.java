package com.testkit.tools.view_value;

import com.alibaba.fastjson.JSONObject;
import com.intellij.openapi.ui.Messages;
import com.testkit.TestkitHelper;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.tools.ToolHelper;
import com.testkit.util.HttpUtil;
import com.testkit.util.JsonUtil;
import com.testkit.util.RemoteScriptCallUtils;
import com.testkit.view.TestkitToolWindow;
import com.testkit.view.TestkitToolWindowFactory;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public class ViewValueLineMarkerProvider implements LineMarkerProvider {

    // Remote Script 超时配置
    private static final int REMOTE_SUBMIT_TIMEOUT = 30;     // 提交请求超时 30 秒
    private static final int REMOTE_RESULT_TIMEOUT = 600;    // 获取结果超时 600 秒

    @Override
    public LineMarkerInfo<PsiIdentifier> getLineMarkerInfo(PsiElement element) {
        // 仅在字段名的标识符（叶子元素）上注册
        if (!(element instanceof PsiIdentifier)) {
            return null;
        }

        // 获取字段名的标识符
        PsiIdentifier identifier = (PsiIdentifier) element;
        PsiElement parent = identifier.getParent();

        // 检查父元素是否为 PsiField
        if (!(parent instanceof PsiField)) {
            return null;
        }

        PsiField field = (PsiField) parent;
        String verifyMsg = test(field);
        if (verifyMsg != null) {
            System.out.println("not_support_view-value, " + verifyMsg + element);
            return null;
        }

        // 创建图标
        Icon icon = AllIcons.CodeWithMe.CwmPermissionView; // 使用IDEA内置图标，您也可以使用自定义图标

        // 返回LineMarkerInfo
        return new LineMarkerInfo<>(
                identifier,
                identifier.getTextRange(),
                icon,
                elem -> "View the field value",
                new GutterIconNavigationHandler<PsiIdentifier>() {
                    @Override
                    public void navigate(MouseEvent mouseEvent, PsiIdentifier psiElement) {
                        PsiClass containingClass = ((PsiField)psiElement.getParent()).getContainingClass();
                        if (containingClass == null) {
                            TestkitHelper.notify(psiElement.getProject(), NotificationType.WARNING, "Can't find class");
                            return;
                        }
                        String projectName = psiElement.getProject().getName();
                        List<String> projectAppList = RuntimeHelper.getAppMetas(projectName).stream().filter(new Predicate<RuntimeHelper.AppMeta>() {
                                    @Override
                                    public boolean test(RuntimeHelper.AppMeta appMeta) {
                                        return ToolHelper.isDependency(psiElement.getParent(), psiElement.getProject(), appMeta.getModule());
                                    }
                                }).map(new Function<RuntimeHelper.AppMeta, String>() {
                                    @Override
                                    public String apply(RuntimeHelper.AppMeta appMeta) {
                                        return appMeta.getApp();
                                    }
                                })
                                .toList();
                        if (CollectionUtils.isEmpty(projectAppList)) {
                            TestkitHelper.notify(psiElement.getProject(), NotificationType.WARNING, "Can't find app, please wait Index build complete");
                            return;
                        }


                        DefaultActionGroup controllerActionGroup = new DefaultActionGroup();
                        List<RuntimeHelper.VisibleApp> visibleApps = RuntimeHelper.getVisibleApps(projectName);
                        for (String app : projectAppList) {
                            List<RuntimeHelper.VisibleApp> list = visibleApps.stream().filter(new Predicate<RuntimeHelper.VisibleApp>() {
                                @Override
                                public boolean test(RuntimeHelper.VisibleApp visibleApp) {
                                    return Objects.equals(app, visibleApp.getAppName());
                                }
                            }).distinct().toList();
                            if (CollectionUtils.isEmpty(list)) {
                                continue;
                            }
                            for (RuntimeHelper.VisibleApp visibleApp : list) {
                                //显示的一个图标加上标题，包含 IP 以区分不同实例
                                String displayName = visibleApp.getAppName() + " (" + visibleApp.getIp() + ":" + visibleApp.getTestkitPort() + ")";
                                AnAction documentation = new AnAction("View the value of " + displayName, "View the value of " + displayName, null) {
                                    @Override
                                    public void actionPerformed(@NotNull AnActionEvent e) {
                                        handleClick(containingClass, (PsiField) psiElement.getParent(), visibleApp);
                                    }
                                };
                                controllerActionGroup.add(documentation); // 将动作添加到动作组中
                            }
                        }


                        if (controllerActionGroup.getChildrenCount() == 0) {
                            TestkitHelper.alert(psiElement.getProject(), Messages.getWarningIcon(), "Can't find runtime app");
                            return;
                        }

                        JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("ViewValuePopup", controllerActionGroup).getComponent();
                        popupMenu.show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
                    }
                },
                GutterIconRenderer.Alignment.LEFT
        );
    }

    private static String test(PsiField field) {
        if (RuntimeHelper.getVisibleApps(field.getProject().getName()).isEmpty()) {
            return "no_runtime_app";
        }
        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null || containingClass.getName() == null) {
            return "containingClassNull";
        }

        if (containingClass.isEnum()) { // PsiClass 提供 isEnum 方法进行检测
            return "is_enum";
        }

        // 获取字段的类型的简单名称
        PsiType fieldType = field.getType();
        String fieldTypeName = fieldType.getPresentableText(); // 获取类型的简单名称

        // 判断字段类型是否为 Logger
        if ("Logger".equals(fieldTypeName)) {
            return "log";
        }

        // 判断字段名称是否为 serialVersionUID
        String fieldName = field.getName();
        if ("serialVersionUID".equals(fieldName)) {
            return "ser";
        }
//        如果是Logger则返回log
//        如果是serialVersionUID 返回 ser


        PsiFile psiFile = field.getContainingFile();
        // 5. 必须不在Java的test模块下
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
            // 获取文件的完整路径
            String filePath = virtualFile.getPath();
            // 检查路径是否以 src/test/java 结尾
            if (filePath.contains("/src/test/java/")) {
                return "is_test_module";
            }
        }

        if (!RuntimeHelper.hasAppMeta(field.getProject().getName())) {
            return "no_meta_data";
        }

        // 2. 必须是非静态方法
        PsiModifierList modifierList = field.getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            return null;
        }


        // 4. 检查类是否含有 Spring 可注册 Bean 的注解
        if (!ToolHelper.isSpringBean(containingClass)) {
            return "not_spring_bean";
        }


        String projectName = field.getProject().getName();
        List<String> projectAppList = RuntimeHelper.getAppMetas(projectName).stream().filter(new Predicate<RuntimeHelper.AppMeta>() {
                    @Override
                    public boolean test(RuntimeHelper.AppMeta appMeta) {
                        return ToolHelper.isDependency(field, field.getProject(), appMeta.getModule());
                    }
                }).map(new Function<RuntimeHelper.AppMeta, String>() {
                    @Override
                    public String apply(RuntimeHelper.AppMeta appMeta) {
                        return appMeta.getApp();
                    }
                })
                .toList();
        if (CollectionUtils.isEmpty(projectAppList)) {
            return "empty app";
        }
        return null;
    }

    private static void handleClick(PsiClass containingClass, PsiField psiField, RuntimeHelper.VisibleApp visibleApp) {
        ProgressManager.getInstance().run(new Task.Backgroundable(psiField.getProject(), "Processing view-value, please wait ...", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                ApplicationManager.getApplication().runReadAction(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject submitRequest = new JSONObject();
                            submitRequest.put("method", "view-value");
                            submitRequest.put("trace", false);
                            JSONObject value = new JSONObject();
                            value.put("typeClass", containingClass.getQualifiedName());
                            value.put("beanName", ToolHelper.getBeanNameFromClass(containingClass));
                            value.put("fieldName", psiField.getName());

                            submitRequest.put("params", value);

                            // Step 1: 提交请求，获取 reqId
                            JSONObject submitRet;
                            if (visibleApp.isRemoteScript()) {
                                submitRet = RemoteScriptCallUtils.sendRequest(psiField.getProject(), visibleApp, submitRequest, REMOTE_SUBMIT_TIMEOUT);
                            } else {
                                submitRet = HttpUtil.sendPost("http://localhost:" + visibleApp.getTestkitPort() + "/", submitRequest, JSONObject.class, 5, 5);
                            }

                            if (submitRet == null || !submitRet.getBooleanValue("success") || submitRet.getString("data") == null) {
                                TestkitHelper.notify(psiField.getProject(), NotificationType.ERROR, "submit req error \n" + (submitRet != null ? submitRet.getString("message") : "null response"));
                                return;
                            }

                            // Step 2: 获取任务结果
                            String reqId = submitRet.getString("data");
                            JSONObject getResultReq = new JSONObject();
                            getResultReq.put("method", "get_task_ret");
                            JSONObject reqParams = new JSONObject();
                            reqParams.put("reqId", reqId);
                            getResultReq.put("params", reqParams);

                            JSONObject result;
                            if (visibleApp.isRemoteScript()) {
                                result = RemoteScriptCallUtils.sendRequest(psiField.getProject(), visibleApp, getResultReq, REMOTE_RESULT_TIMEOUT);
                            } else {
                                result = HttpUtil.sendPost("http://localhost:" + visibleApp.getTestkitPort() + "/", getResultReq, JSONObject.class, 5, 5);
                            }
                            if (result == null) {
                                TestkitHelper.notify(psiField.getProject(), NotificationType.ERROR, "req is error\n result is null");
                            } else if (!result.getBooleanValue("success")) {
                                TestkitHelper.notify(psiField.getProject(), NotificationType.ERROR, "req is error\n" + result.getString("message"));
                            } else {
                                Object data = result.get("data");
                                if (data == null) {
                                    TestkitHelper.showMessageWithCopy(psiField.getProject(), "null");
                                } else if (data instanceof String
                                        || data instanceof Byte
                                        || data instanceof Short
                                        || data instanceof Integer
                                        || data instanceof Long
                                        || data instanceof Float
                                        || data instanceof Double
                                        || data instanceof Character
                                        || data instanceof Boolean
                                        || data.getClass().isEnum()) {
                                    TestkitHelper.showMessageWithCopy(psiField.getProject(), data.toString());
                                } else {
                                    TestkitHelper.showMessageWithCopy(psiField.getProject(), JsonUtil.formatObj(data));
                                }
                            }
                        } catch (Throwable ex) {
                            TestkitHelper.notify(psiField.getProject(), NotificationType.ERROR, "wait ret is error\n" + ToolHelper.getStackTrace(ex));
                        }
                    }
                });

            }
        });
    }


}
