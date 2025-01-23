package com.testkit.tools.view_value;

import com.alibaba.fastjson.JSONObject;
import com.testkit.TestkitHelper;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.tools.ToolHelper;
import com.testkit.util.HttpUtil;
import com.testkit.util.JsonUtil;
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


    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {
        if (!(element instanceof PsiField)) {
            return null;
        }
        PsiField field = (PsiField) element;
        String verifyMsg = test(field);
        if(verifyMsg!=null){
            System.out.println("not_support_view-value, " + verifyMsg + element);
            return null;
        }

        // 创建图标
        Icon icon = AllIcons.CodeWithMe.CwmPermissionView; // 使用IDEA内置图标，您也可以使用自定义图标

        // 返回LineMarkerInfo
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                icon,
                elem -> "View the field value",
                new GutterIconNavigationHandler<PsiElement>() {
                    @Override
                    public void navigate(MouseEvent mouseEvent, PsiElement psiElement) {
                        PsiClass containingClass = ((PsiField) psiElement).getContainingClass();
                        if (containingClass == null) {
                            TestkitHelper.notify(element.getProject(), NotificationType.WARNING, "Can't find class");
                            return;
                        }
                        String projectName = element.getProject().getName();
                        List<String> projectAppList = RuntimeHelper.getAppMetas(projectName).stream().filter(new Predicate<RuntimeHelper.AppMeta>() {
                                    @Override
                                    public boolean test(RuntimeHelper.AppMeta appMeta) {
                                        return ToolHelper.isDependency(element, element.getProject(), appMeta.getModule());
                                    }
                                }).map(new Function<RuntimeHelper.AppMeta, String>() {
                                    @Override
                                    public String apply(RuntimeHelper.AppMeta appMeta) {
                                        return appMeta.getApp();
                                    }
                                })
                                .toList();
                        if (CollectionUtils.isEmpty(projectAppList)) {
                            TestkitHelper.notify(element.getProject(), NotificationType.WARNING, "Can't find app, please wait Index build complete");
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
                            }).toList();
                            if (CollectionUtils.isEmpty(list)) {
                                continue;
                            }
                            for (RuntimeHelper.VisibleApp visibleApp : list) {
                                //显示的一个图标加上标题
                                AnAction documentation = new AnAction("View the value of " + visibleApp.getAppName() + ":" + visibleApp.getPort(), "View the value of " + visibleApp.getAppName() + ":" + visibleApp.getPort(), null) {
                                    @Override
                                    public void actionPerformed(@NotNull AnActionEvent e) {
                                        handleClick(element, containingClass, psiElement, visibleApp);
                                    }
                                };
                                controllerActionGroup.add(documentation); // 将动作添加到动作组中
                            }
                        }


                        if (controllerActionGroup.getChildrenCount() == 0) {
                            TestkitHelper.notify(element.getProject(), NotificationType.WARNING, "Can't find runtime app");
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

        if(!RuntimeHelper.hasAppMeta(field.getProject().getName()) || !SettingsStorageHelper.isEnableSideServer(field.getProject())){
            return "no_side_server";
        }

        // 2. 必须是非静态方法
        PsiModifierList modifierList = field.getModifierList();
        if (modifierList !=null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
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

    private static void handleClick(PsiElement element, PsiClass containingClass, PsiElement psiElement, RuntimeHelper.VisibleApp visibleApp) {
        ProgressManager.getInstance().run(new Task.Backgroundable(element.getProject(), "Processing view-value, please wait ...", false) {
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
                            value.put("fieldName", ((PsiField) psiElement).getName());

                            submitRequest.put("params", value);

                            JSONObject submitRet = HttpUtil.sendPost("http://localhost:" + visibleApp.getSidePort() + "/", submitRequest, JSONObject.class);
                            if (submitRet == null || !submitRet.getBooleanValue("success") || submitRet.getString("data") == null) {
                                TestkitHelper.notify(element.getProject(), NotificationType.ERROR, "submit req error \n" + submitRet.getString("message"));
                                return;
                            }

                            String reqId = submitRet.getString("data");
                            HashMap<String, Object> map = new HashMap<>();
                            map.put("method", "get_task_ret");
                            HashMap<Object, Object> params = new HashMap<>();
                            params.put("reqId", reqId);
                            map.put("params", params);

                            JSONObject result = HttpUtil.sendPost("http://localhost:" + visibleApp.getSidePort() + "/", map, JSONObject.class);
                            if (result == null) {
                                TestkitHelper.notify(element.getProject(), NotificationType.ERROR, "req is error\n result is null");
                            } else if (!result.getBooleanValue("success")) {
                                TestkitHelper.notify(element.getProject(), NotificationType.ERROR, "req is error\n" + result.getString("message"));
                            } else {
                                Object data = result.get("data");
                                if (data == null) {
                                    TestkitHelper.showMessageWithCopy(element.getProject(),"null");
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
                                    TestkitHelper.showMessageWithCopy(element.getProject(),  data.toString());
                                } else {
                                    TestkitHelper.showMessageWithCopy(element.getProject(),  JsonUtil.formatObj(data));
                                }
                            }
                        } catch (Throwable ex) {
                            TestkitHelper.notify(element.getProject(), NotificationType.ERROR, "wait ret is error\n" + ToolHelper.getStackTrace(ex));
                        }
                    }
                });

            }
        });
    }


}
