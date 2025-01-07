package com.fling.tools.view_value;

import com.alibaba.fastjson.JSONObject;
import com.fling.FlingHelper;
import com.fling.RuntimeAppHelper;
import com.fling.tools.ToolHelper;
import com.fling.util.HttpUtil;
import com.fling.util.JsonUtil;
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
                            FlingHelper.notify(element.getProject(), NotificationType.WARNING, "Can't find class");
                            return;
                        }
                        String projectName = element.getProject().getName();
                        List<String> projectAppList = RuntimeAppHelper.getAppMetas(projectName).stream().filter(new Predicate<RuntimeAppHelper.AppMeta>() {
                                    @Override
                                    public boolean test(RuntimeAppHelper.AppMeta appMeta) {
                                        return ToolHelper.isDependency(element, element.getProject(), appMeta.getModule());
                                    }
                                }).map(new Function<RuntimeAppHelper.AppMeta, String>() {
                                    @Override
                                    public String apply(RuntimeAppHelper.AppMeta appMeta) {
                                        return appMeta.getApp();
                                    }
                                })
                                .toList();
                        if (CollectionUtils.isEmpty(projectAppList)) {
                            FlingHelper.notify(element.getProject(), NotificationType.WARNING, "Can't find app, please wait Index build complete");
                            return;
                        }


                        DefaultActionGroup controllerActionGroup = new DefaultActionGroup();
                        List<RuntimeAppHelper.VisibleApp> visibleApps = RuntimeAppHelper.getVisibleApps(projectName);
                        for (String app : projectAppList) {
                            List<RuntimeAppHelper.VisibleApp> list = visibleApps.stream().filter(new Predicate<RuntimeAppHelper.VisibleApp>() {
                                @Override
                                public boolean test(RuntimeAppHelper.VisibleApp visibleApp) {
                                    return Objects.equals(app, visibleApp.getAppName());
                                }
                            }).toList();
                            if (CollectionUtils.isEmpty(list)) {
                                continue;
                            }
                            for (RuntimeAppHelper.VisibleApp visibleApp : list) {
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
                            FlingHelper.notify(element.getProject(), NotificationType.WARNING, "Can't find runtime app");
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
        List<String> projectAppList = RuntimeAppHelper.getAppMetas(projectName).stream().filter(new Predicate<RuntimeAppHelper.AppMeta>() {
                    @Override
                    public boolean test(RuntimeAppHelper.AppMeta appMeta) {
                        return ToolHelper.isDependency(field, field.getProject(), appMeta.getModule());
                    }
                }).map(new Function<RuntimeAppHelper.AppMeta, String>() {
                    @Override
                    public String apply(RuntimeAppHelper.AppMeta appMeta) {
                        return appMeta.getApp();
                    }
                })
                .toList();
        if (CollectionUtils.isEmpty(projectAppList)) {
            return "empty app";
        }
        return null;
    }

    private static void handleClick(PsiElement element, PsiClass containingClass, PsiElement psiElement, RuntimeAppHelper.VisibleApp visibleApp) {
        ProgressManager.getInstance().run(new Task.Backgroundable(element.getProject(), "Processing view-value, please wait ...", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                ApplicationManager.getApplication().runReadAction(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject submitRequest = new JSONObject();
                            submitRequest.put("method", "view-value");
                            submitRequest.put("monitor", false);
                            JSONObject value = new JSONObject();
                            value.put("typeClass", containingClass.getQualifiedName());
                            value.put("beanName", ToolHelper.getBeanNameFromClass(containingClass));
                            ;
                            value.put("fieldName", ((PsiField) psiElement).getName());

                            submitRequest.put("params", value);

                            JSONObject submitRet = HttpUtil.sendPost("http://localhost:" + visibleApp.getSidePort() + "/", submitRequest, JSONObject.class);
                            if (submitRet == null || !submitRet.getBooleanValue("success") || submitRet.getString("data") == null) {
                                FlingHelper.notify(element.getProject(), NotificationType.ERROR, "submit req error \n" + submitRet.getString("message"));
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
                                FlingHelper.notify(element.getProject(), NotificationType.ERROR, "req is error\n result is null");
                            } else if (!result.getBooleanValue("success")) {
                                FlingHelper.notify(element.getProject(), NotificationType.ERROR, "req is error\n" + result.getString("message"));
                            } else {
                                Object data = result.get("data");
                                if (data == null) {
                                    FlingHelper.showMessageWithCopy(element.getProject(),"null");
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
                                    FlingHelper.showMessageWithCopy(element.getProject(),  data.toString());
                                } else {
                                    FlingHelper.showMessageWithCopy(element.getProject(),  JsonUtil.formatObj(data));
                                }
                            }
                        } catch (Throwable ex) {
                            FlingHelper.notify(element.getProject(), NotificationType.ERROR, "wait ret is error\n" + ToolHelper.getStackTrace(ex));
                        }
                    }
                });

            }
        });
    }


}
