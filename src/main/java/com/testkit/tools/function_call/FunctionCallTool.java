package com.testkit.tools.function_call;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.intellij.notification.NotificationType;
import com.testkit.TestkitHelper;
import com.testkit.ReqStorageHelper;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.tools.ToolHelper;
import com.testkit.util.Container;
import com.testkit.view.TestkitToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.testkit.tools.BasePluginTool;
import com.testkit.tools.PluginToolEnum;
import com.intellij.util.ui.JBUI;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FunctionCallTool extends BasePluginTool {

    public static final Icon FUNCTION_CALL_DISABLE_ICON = IconLoader.getIcon("/icons/function-call-disable.svg", FunctionCallTool.class);

    public static final Icon PROXY_DISABLE_ICON = IconLoader.getIcon("/icons/proxy-disable.svg", FunctionCallTool.class);
    public static final Icon PROXY_ICON = IconLoader.getIcon("/icons/proxy.svg", FunctionCallTool.class);
    public static final Icon CONTROLLER_ICON = IconLoader.getIcon("/icons/controller.svg", FunctionCallTool.class);
    public static final Icon FEIGN_ICON = IconLoader.getIcon("/icons/feign.svg", FunctionCallTool.class);

    public static final Icon KIcon = IconLoader.getIcon("/icons/k.svg", FunctionCallTool.class);


    private JButton controllerCommandButton;
    private JButton feignCommandButton;
    private JButton runButton;

    private JButton getKeyButton;
    private JButton getValButton;
    private JButton delValButton;
    private JComboBox<ToolHelper.MethodAction> actionComboBox;

    private JToggleButton useProxyButton;

    {
        this.tool = PluginToolEnum.FUNCTION_CALL;
    }

    public FunctionCallTool(TestkitToolWindow testkitToolWindow) {
        super(testkitToolWindow);

        buildControllerButton(testkitToolWindow);
        buildFeignButton(testkitToolWindow);
        buildSpringCacheButton(testkitToolWindow);
    }

    private void buildSpringCacheButton(TestkitToolWindow testkitToolWindow) {
        getKeyButton = new JButton(KIcon);
        getKeyButton.setToolTipText("[Spring-cache] Build keys");
        getValButton = new JButton(AllIcons.Actions.Find);
        getValButton.setToolTipText("[Spring-cache] Build keys and get values for every key");
        delValButton = new JButton(AllIcons.Actions.GC);
        delValButton.setToolTipText("[Spring-cache] Build keys and delete these");
        //        // 设置按钮大小
        Dimension buttonSize = new Dimension(30, 30);
        getKeyButton.setPreferredSize(buttonSize);
        getValButton.setPreferredSize(buttonSize);
        delValButton.setPreferredSize(buttonSize);

        getKeyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                RuntimeHelper.VisibleApp app = RuntimeHelper.getSelectedApp(getProject().getName());
                if (app == null) {
                    Messages.showMessageDialog(getProject(),
                            "Failed to find runtime app",
                            "Error",
                            Messages.getErrorIcon());
                    return;
                }
                triggerHttpTask(getKeyButton, KIcon, app.getTestkitPort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        return handleCacheAction("build_cache_key");
                    }
                });
            }
        });

        getValButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                RuntimeHelper.VisibleApp app = RuntimeHelper.getSelectedApp(getProject().getName());
                if (app == null) {
                    Messages.showMessageDialog(getProject(),
                            "Failed to find runtime app",
                            "Error",
                            Messages.getErrorIcon());
                    return;
                }
                triggerHttpTask(getValButton, AllIcons.Actions.Find, app.getTestkitPort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        return handleCacheAction("get_cache");
                    }
                });
            }
        });

        delValButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                RuntimeHelper.VisibleApp app = RuntimeHelper.getSelectedApp(getProject().getName());
                if (app == null) {
                    Messages.showMessageDialog(getProject(),
                            "Failed to find runtime app",
                            "Error",
                            Messages.getErrorIcon());
                    return;
                }
                triggerHttpTask(delValButton, AllIcons.Actions.GC, app.getTestkitPort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        return handleCacheAction("delete_cache");
                    }
                });
            }
        });
    }

    private void buildControllerButton(TestkitToolWindow testkitToolWindow) {
        controllerCommandButton = new JButton(CONTROLLER_ICON);
        controllerCommandButton.setPreferredSize(new Dimension(32, 32));
        controllerCommandButton.setToolTipText("[Controller] Generate controller command");
        controllerCommandButton.addActionListener(e -> {
            ToolHelper.MethodAction selectedItem = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
            if (selectedItem == null) {
                TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Please select a method");
                return;
            }
            List<String> projectAppList = RuntimeHelper.getAppMetas(toolWindow.getProject().getName()).stream().filter(new Predicate<RuntimeHelper.AppMeta>() {
                        @Override
                        public boolean test(RuntimeHelper.AppMeta appMeta) {
                            return ToolHelper.isDependency(selectedItem.getMethod(), getProject(), appMeta.getModule());
                        }
                    }).map(new Function<RuntimeHelper.AppMeta, String>() {
                        @Override
                        public String apply(RuntimeHelper.AppMeta appMeta) {
                            return appMeta.getApp();
                        }
                    })
                    .toList();

            DefaultActionGroup controllerActionGroup = new DefaultActionGroup();
            if (CollectionUtils.isNotEmpty(projectAppList)) {
                for (String app : projectAppList) {
                    SettingsStorageHelper.HttpCommand controllerCommand = SettingsStorageHelper.getAppControllerCommand(testkitToolWindow.getProject(), app);
                    String script = controllerCommand.getScript();
                    List<String> envs = controllerCommand.getEnvs();
                    if (CollectionUtils.isEmpty(envs) && Objects.equals(script, SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript())) {
                        continue;
                    }
                    if (CollectionUtils.isEmpty(envs)) {
                        envs = new ArrayList<>();
                        envs.add(null);
                    }
                    for (String env : envs) {
                        //显示的一个图标加上标题
                        AnAction documentation = new AnAction("Generate with " + app + ":" + env, "Generate with " + app + ":" + env, CONTROLLER_ICON) {
                            @Override
                            public void actionPerformed(@NotNull AnActionEvent e) {
                                Application application = ApplicationManager.getApplication();
                                ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), "Processing generate function, please wait ...", false) {

                                    @Override
                                    public void run(@NotNull ProgressIndicator indicator) {
                                        application.runReadAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                handleControllerCommand(env, script, selectedItem.getMethod());
                                            }
                                        });
                                    }
                                });
                            }
                        };
                        controllerActionGroup.add(documentation); // 将动作添加到动作组中
                    }
                }
            }


            if (controllerActionGroup.getChildrenCount() == 0) {
                //没有自定义逻辑，则直接处理

                Application application = ApplicationManager.getApplication();
                ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), "Processing generate function, please wait ...", false) {

                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        application.runReadAction(new Runnable() {
                            @Override
                            public void run() {
                                handleControllerCommand(null, SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript(), selectedItem.getMethod());
                            }
                        });
                    }
                });

                return;
            }

            JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("ControllerCommandPopup", controllerActionGroup).getComponent();
            popupMenu.show(controllerCommandButton, 0, 0);
        });
    }

    private void buildFeignButton(TestkitToolWindow testkitToolWindow) {
        feignCommandButton = new JButton(FEIGN_ICON);
        feignCommandButton.setPreferredSize(new Dimension(32, 32));
        feignCommandButton.setToolTipText("[FeignClient] Generate feign command");
        feignCommandButton.addActionListener(e -> {
            ToolHelper.MethodAction selectedItem = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
            if (selectedItem == null) {
                TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Please select a method");
                return;
            }
            List<String> projectAppList = RuntimeHelper.getAppMetas(toolWindow.getProject().getName()).stream().filter(new Predicate<RuntimeHelper.AppMeta>() {
                        @Override
                        public boolean test(RuntimeHelper.AppMeta appMeta) {
                            return ToolHelper.isDependency(selectedItem.getMethod(), getProject(), appMeta.getModule());
                        }
                    }).map(new Function<RuntimeHelper.AppMeta, String>() {
                        @Override
                        public String apply(RuntimeHelper.AppMeta appMeta) {
                            return appMeta.getApp();
                        }
                    })
                    .toList();

            DefaultActionGroup feignActionGroup = new DefaultActionGroup();
            if (CollectionUtils.isNotEmpty(projectAppList)) {
                for (String app : projectAppList) {
                    SettingsStorageHelper.HttpCommand appFeignCommand = SettingsStorageHelper.getAppFeignCommand(testkitToolWindow.getProject(), app);
                    String script = appFeignCommand.getScript();
                    List<String> envs = appFeignCommand.getEnvs();
                    if (CollectionUtils.isEmpty(envs) && Objects.equals(script, SettingsStorageHelper.DEF_FEIGN_COMMAND.getScript())) {
                        continue;
                    }
                    if (CollectionUtils.isEmpty(envs)) {
                        envs = new ArrayList<>();
                        envs.add(null);
                    }
                    for (String env : envs) {
                        //显示的一个图标加上标题
                        AnAction documentation = new AnAction("Generate with " + app + ":" + env, "Generate with " + app + ":" + env, FEIGN_ICON) {
                            @Override
                            public void actionPerformed(@NotNull AnActionEvent e) {
                                Application application = ApplicationManager.getApplication();
                                ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), "Processing generate function, please wait ...", false) {

                                    @Override
                                    public void run(@NotNull ProgressIndicator indicator) {
                                        application.runReadAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                handleFeignCommand(env, script, selectedItem.getMethod());
                                            }
                                        });
                                    }
                                });
                            }
                        };
                        feignActionGroup.add(documentation); // 将动作添加到动作组中
                    }
                }
            }


            if (feignActionGroup.getChildrenCount() == 0) {
                //没有自定义逻辑，则直接处理

                Application application = ApplicationManager.getApplication();
                ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), "Processing generate function, please wait ...", false) {

                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        application.runReadAction(new Runnable() {
                            @Override
                            public void run() {
                                handleFeignCommand(null, SettingsStorageHelper.DEF_FEIGN_COMMAND.getScript(), selectedItem.getMethod());
                            }
                        });
                    }
                });

                return;
            }

            JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("FeignCommandPopup", feignActionGroup).getComponent();
            popupMenu.show(feignCommandButton, 0, 0);
        });
    }


    protected boolean canStore() {
        return true;
    }

    protected List<String> verifyNowStore() {
        ToolHelper.MethodAction methodAction = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
        if (methodAction == null || !methodAction.getMethod().isValid()) {
            throw new RuntimeException("Please select a valid method");
        }
        try {
            JSONObject.parseObject(jsonInputField.getText().trim());
        } catch (Exception e) {
            throw new RuntimeException("Input parameter must be json object");
        }
        return RuntimeHelper.getAppMetas(toolWindow.getProject().getName()).stream().filter(new Predicate<RuntimeHelper.AppMeta>() {
                    @Override
                    public boolean test(RuntimeHelper.AppMeta appMeta) {
                        return ToolHelper.isDependency(methodAction.getMethod(), getProject(), appMeta.getModule());
                    }
                }).map(new Function<RuntimeHelper.AppMeta, String>() {
                    @Override
                    public String apply(RuntimeHelper.AppMeta appMeta) {
                        return appMeta.getApp();
                    }
                })
                .toList();
    }


    protected void handleStore(String app, String group, String title) {
        PsiMethod method = ((ToolHelper.MethodAction) actionComboBox.getSelectedItem()).getMethod();
        String text = jsonInputField.getText();
        JSONObject args = null;
        try {
            args = JSONObject.parseObject(text);
        } catch (Exception ex) {
            TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Input parameter must be json object");
            return;
        }
        PsiClass containingClass = method.getContainingClass();
        String typeClass = containingClass.getQualifiedName();
        String beanName = ToolHelper.getBeanNameFromClass(containingClass);

        PsiParameter[] parameters = method.getParameterList().getParameters();
        String[] argTypes = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            argTypes[i] = parameters[i].getType().getCanonicalText();
        }

        List<String> argNames = ToolHelper.fetchNames(method);

        ReqStorageHelper.SavedReq savedReq = new ReqStorageHelper.SavedReq();
        savedReq.setTitle(StringUtils.isBlank(title) ? "undefined" : title);
        savedReq.setArgs(args);
        ReqStorageHelper.FunctionCallMeta methodMeta = new ReqStorageHelper.FunctionCallMeta();
        methodMeta.setTypeClass(typeClass);
        methodMeta.setBeanName(beanName);
        methodMeta.setMethodName(method.getName());
        methodMeta.setArgNames(argNames);
        methodMeta.setArgTypes(JSONObject.toJSONString(argTypes));
        methodMeta.setUseInterceptor(useInterceptor);
        List<Component> actionComponents = Arrays.asList(actionPanel.getComponents());
        methodMeta.setSpringCache(actionComponents.contains(getKeyButton));
        ReqStorageHelper.SubItemType subItemType = null;
        if (actionComponents.contains(controllerCommandButton)) {
            subItemType = ReqStorageHelper.SubItemType.controller;
            methodMeta.setHttpMeta(parseControllerMeta(method));
        } else if (actionComponents.contains(feignCommandButton)) {
            subItemType = ReqStorageHelper.SubItemType.feign_client;
            methodMeta.setHttpMeta(parseFeignMeta(method));
        }
        methodMeta.setSubType(subItemType);
        ReqStorageHelper.saveAppReq(getProject(), app, StringUtils.isBlank(group) ? "undefined" : group, ReqStorageHelper.ItemType.function_call, ToolHelper.buildMethodKey(method), methodMeta, savedReq.getTitle(), savedReq);
    }


    public ReqStorageHelper.HttpCommandMeta parseControllerMeta(PsiMethod method) {
        ReqStorageHelper.HttpCommandMeta commandMeta = new ReqStorageHelper.HttpCommandMeta();

        String httpMethod = "GET"; // Default to GET
        String path1 = "/";

        PsiClass containingClass = method.getContainingClass();
        if (containingClass.hasAnnotation("org.springframework.web.bind.annotation.RequestMapping")) {
            PsiAnnotation annotation = containingClass.getAnnotation("org.springframework.web.bind.annotation.RequestMapping");
            String controllerPath = ToolHelper.getAnnotationValueText(annotation.findAttributeValue("value"));
            if (StringUtils.isBlank(controllerPath)) {
                controllerPath = ToolHelper.getAnnotationValueText(annotation.findAttributeValue("path"));
            }
            if (StringUtils.isNotBlank(controllerPath) && controllerPath.startsWith("/")) {
                path1 += controllerPath.substring(1);
            } else {
                path1 += controllerPath;
            }
        }

        String[] requestMethodAnnotations = new String[]{
                "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.GetMapping",
                "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PutMapping",
                "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PatchMapping"
        };


        // Check for method-level annotations to determine HTTP method and path
        for (String annotationFqn : requestMethodAnnotations) {
            PsiAnnotation methodAnnotation = method.getModifierList().findAnnotation(annotationFqn);
            if (methodAnnotation == null) {
                continue;
            }
            if (annotationFqn.contains("GetMapping")) {
                httpMethod = "GET";
            } else if (annotationFqn.contains("PostMapping")) {
                httpMethod = "POST";
            } else if (annotationFqn.contains("PutMapping")) {
                httpMethod = "PUT";
            } else if (annotationFqn.contains("DeleteMapping")) {
                httpMethod = "DELETE";
            } else if (annotationFqn.contains("PatchMapping")) {
                httpMethod = "PATCH";
            } else if (annotationFqn.contains("RequestMapping")) {
                //从注解中的method属性取出一个 method,注意,method 是个数组,随便取一个就好
                String mappingMethod = ToolHelper.getAnnotationValueText(methodAnnotation.findAttributeValue("method"));
                if (StringUtils.isNotBlank(mappingMethod)) {
                    httpMethod = mappingMethod.toUpperCase();
                }
            }

            String reqPath = ToolHelper.getAnnotationValueText(methodAnnotation.findAttributeValue("value"));
            if (StringUtils.isBlank(reqPath)) {
                reqPath = ToolHelper.getAnnotationValueText(methodAnnotation.findAttributeValue("path"));
            }

            if (StringUtils.isNotBlank(reqPath)) {
                if (path1.endsWith("/")) {
                    if (reqPath.startsWith("/")) {
                        path1 += reqPath.substring(1);
                    } else {
                        path1 += reqPath;
                    }
                } else {
                    if (reqPath.startsWith("/")) {
                        path1 += reqPath;
                    } else {
                        path1 += ("/" + reqPath);
                    }
                }
            }
            break;
        }

        // Process parameters
        // Get the parameters
        PsiParameter[] parameters = method.getParameterList().getParameters();
        ArrayList<String> paths = new ArrayList<>();
        Map<String, String> aliasmap = new HashMap<>();
        String jsonBodyKey = null;
        Map<String, String> headers = new HashMap<>();
        for (PsiParameter parameter : parameters) {
            PsiAnnotation requestBodyAnnotation = parameter.getModifierList().findAnnotation("org.springframework.web.bind.annotation.RequestBody");
            if (requestBodyAnnotation != null) {
                if (httpMethod.equals("GET")) {
                    httpMethod = "POST";
                }
                jsonBodyKey = parameter.getName();
                continue;
            }
            PsiAnnotation headerAnnotation = parameter.getModifierList().findAnnotation("org.springframework.web.bind.annotation.RequestHeader");
            if (headerAnnotation != null) {
                String headerKey = ToolHelper.getAnnotationValueText(headerAnnotation.findAttributeValue("value"));
                if (StringUtils.isBlank(headerKey)) {
                    headerKey = ToolHelper.getAnnotationValueText(headerAnnotation.findAttributeValue("name"));
                }
                if (StringUtils.isBlank(headerKey)) {
                    headerKey = parameter.getName();
                }
                headers.put(parameter.getName(), headerKey);
                continue;
            }


            //判断是否含有 requestParam注解,如果有,则将别名注册到 aliasmap 中,key 是形参名,val 是 requestparam 的配置
            // 如果不存在 @RequestBody 注解，则检查 @RequestParam 注解
            PsiAnnotation requestParamAnnotation = parameter.getModifierList().findAnnotation("org.springframework.web.bind.annotation.RequestParam");
            boolean path = false;
            if (requestParamAnnotation == null) {
                requestParamAnnotation = parameter.getModifierList().findAnnotation("org.springframework.web.bind.annotation.PathVariable");
                path = true;
            }
            if (requestParamAnnotation == null) {
                continue;
            }

            if (path) {
                paths.add(parameter.getName());
            }

            String alias = ToolHelper.getAnnotationValueText(requestParamAnnotation.findAttributeValue("value"));
            if (StringUtils.isBlank(alias)) {
                alias = ToolHelper.getAnnotationValueText(requestParamAnnotation.findAttributeValue("name"));
            }
            if (StringUtils.isNotBlank(alias)) {
                // 将别名注册到 aliasMap 中
                aliasmap.put(parameter.getName(), alias);
            }
        }

        commandMeta.setHttpMethod(httpMethod);
        commandMeta.setPath(path1);
        commandMeta.setAliasmap(aliasmap);
        commandMeta.setJsonBodyKey(jsonBodyKey);
        commandMeta.setPathKeys(paths);
        commandMeta.setHeaders(headers);
        return commandMeta;
    }

    public ReqStorageHelper.HttpCommandMeta parseFeignMeta(PsiMethod method) {
        ReqStorageHelper.HttpCommandMeta commandMeta = new ReqStorageHelper.HttpCommandMeta();

        String httpMethod = "GET"; // Default to GET
        String path1 = "/";
        String feignName = null;
        String feignUrl = null;
        PsiClass containingClass = method.getContainingClass();
        if (containingClass.hasAnnotation("org.springframework.cloud.openfeign.FeignClient")) {
            PsiAnnotation annotation = containingClass.getAnnotation("org.springframework.cloud.openfeign.FeignClient");
            String rootPath = ToolHelper.getAnnotationValueText(annotation.findAttributeValue("path"));
            feignName = ToolHelper.getAnnotationValueText(annotation.findAttributeValue("name"));
            if (StringUtils.isBlank(feignName)) {
                feignName = ToolHelper.getAnnotationValueText(annotation.findAttributeValue("value"));
            }
            feignUrl = ToolHelper.getAnnotationValueText(annotation.findAttributeValue("url"));
            if (StringUtils.isBlank(feignUrl) || (feignUrl.startsWith("${") && feignUrl.endsWith("}"))) {
                feignUrl = null;
            }
            if (StringUtils.isNotBlank(rootPath) && rootPath.startsWith("/")) {
                path1 += rootPath.substring(1);
            } else {
                path1 += rootPath;
            }
        }

        String[] requestMethodAnnotations = new String[]{
                "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.GetMapping",
                "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PutMapping",
                "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PatchMapping"
        };


        // Check for method-level annotations to determine HTTP method and path
        for (String annotationFqn : requestMethodAnnotations) {
            PsiAnnotation methodAnnotation = method.getModifierList().findAnnotation(annotationFqn);
            if (methodAnnotation == null) {
                continue;
            }
            if (annotationFqn.contains("GetMapping")) {
                httpMethod = "GET";
            } else if (annotationFqn.contains("PostMapping")) {
                httpMethod = "POST";
            } else if (annotationFqn.contains("PutMapping")) {
                httpMethod = "PUT";
            } else if (annotationFqn.contains("DeleteMapping")) {
                httpMethod = "DELETE";
            } else if (annotationFqn.contains("PatchMapping")) {
                httpMethod = "PATCH";
            } else if (annotationFqn.contains("RequestMapping")) {
                //从注解中的method属性取出一个 method,注意,method 是个数组,随便取一个就好
                String mappingMethod = ToolHelper.getAnnotationValueText(methodAnnotation.findAttributeValue("method"));
                if (StringUtils.isNotBlank(mappingMethod)) {
                    httpMethod = mappingMethod.toUpperCase();
                }
            }

            String reqPath = ToolHelper.getAnnotationValueText(methodAnnotation.findAttributeValue("value"));
            if (StringUtils.isBlank(reqPath)) {
                reqPath = ToolHelper.getAnnotationValueText(methodAnnotation.findAttributeValue("path"));
            }


            if (StringUtils.isNotBlank(reqPath)) {
                if (path1.endsWith("/")) {
                    if (reqPath.startsWith("/")) {
                        path1 += reqPath.substring(1);
                    } else {
                        path1 += reqPath;
                    }
                } else {
                    if (reqPath.startsWith("/")) {
                        path1 += reqPath;
                    } else {
                        path1 += ("/" + reqPath);
                    }
                }
            }
            break;
        }

        // Process parameters
        // Get the parameters
        PsiParameter[] parameters = method.getParameterList().getParameters();
        ArrayList<String> paths = new ArrayList<>();
        Map<String, String> aliasmap = new HashMap<>();
        String jsonBodyKey = null;
        Map<String, String> headers = new HashMap<>();
        for (PsiParameter parameter : parameters) {
            PsiAnnotation requestBodyAnnotation = parameter.getModifierList().findAnnotation("org.springframework.web.bind.annotation.RequestBody");
            if (requestBodyAnnotation != null) {
                if (httpMethod.equals("GET")) {
                    httpMethod = "POST";
                }
                jsonBodyKey = parameter.getName();
                continue;
            }

            PsiAnnotation headerAnnotation = parameter.getModifierList().findAnnotation("org.springframework.web.bind.annotation.RequestHeader");
            if (headerAnnotation != null) {
                String headerKey = ToolHelper.getAnnotationValueText(headerAnnotation.findAttributeValue("value"));
                if (StringUtils.isBlank(headerKey)) {
                    headerKey = ToolHelper.getAnnotationValueText(headerAnnotation.findAttributeValue("name"));
                }
                if (StringUtils.isBlank(headerKey)) {
                    headerKey = parameter.getName();
                }
                headers.put(parameter.getName(), headerKey);
                continue;
            }

            //判断是否含有 requestParam注解,如果有,则将别名注册到 aliasmap 中,key 是形参名,val 是 requestparam 的配置
            // 如果不存在 @RequestBody 注解，则检查 @RequestParam 注解
            PsiAnnotation requestParamAnnotation = parameter.getModifierList().findAnnotation("org.springframework.web.bind.annotation.RequestParam");
            boolean path = false;
            if (requestParamAnnotation == null) {
                requestParamAnnotation = parameter.getModifierList().findAnnotation("org.springframework.web.bind.annotation.PathVariable");
                path = true;
            }
            if (requestParamAnnotation == null) {
                continue;
            }

            if (path) {
                paths.add(parameter.getName());
            }
            // 获取 @RequestParam 注解中的配置值
            String alias = ToolHelper.getAnnotationValueText(requestParamAnnotation.findAttributeValue("value"));
            if (StringUtils.isBlank(alias)) {
                alias = ToolHelper.getAnnotationValueText(requestParamAnnotation.findAttributeValue("name"));
            }
            if (StringUtils.isNotBlank(alias)) {
                // 将别名注册到 aliasMap 中
                aliasmap.put(parameter.getName(), alias);
            }
        }

        commandMeta.setHttpMethod(httpMethod);
        commandMeta.setPath(path1);
        commandMeta.setAliasmap(aliasmap);
        commandMeta.setJsonBodyKey(jsonBodyKey);
        commandMeta.setPathKeys(paths);
        commandMeta.setFeignName(feignName);
        commandMeta.setFeignUrl(feignUrl);
        commandMeta.setHeaders(headers);
        return commandMeta;
    }

    private void handleControllerCommand(String env, String script, PsiMethod method) {
        String jsonParams = jsonInputField.getText();
        JSONObject inputParams = null;
        try {
            inputParams = JSONObject.parseObject(jsonParams);
        } catch (Exception e) {
            setOutputText("Input parameter must be json object");
            return;
        }
        ReqStorageHelper.HttpCommandMeta commandMeta = parseControllerMeta(method);
        String httpMethod = commandMeta.getHttpMethod();
        String path1 = commandMeta.getPath();
        Map<String, String> aliasmap = commandMeta.getAliasmap();
        List<String> pathKeys = commandMeta.getPathKeys();
        String jsonBodyKey = commandMeta.getJsonBodyKey();
        Map<String, String> headers = commandMeta.getHeaders();
        Map<String, String> headerValues = new HashMap<>();

        String jsonBody = null;
        if (jsonBodyKey != null) {
            if (inputParams.get(jsonBodyKey) != null) {
                jsonBody = JSONObject.toJSONString(inputParams.get(jsonBodyKey), SerializerFeature.WriteMapNullValue);
            } else {
                jsonBody = "{}";
            }
            inputParams.remove(jsonBodyKey);
        }

        if (headers != null) {
            for (Map.Entry<String, String> stringEntry : headers.entrySet()) {
                String headerVal = inputParams.getString(stringEntry.getKey());
                inputParams.remove(stringEntry.getKey());
                if (headerVal == null) {
                    continue;
                }
                headerValues.put(stringEntry.getValue(), headerVal);
            }
        }


//               parse是个双层 map 参数
//                我想让双层key展开平铺
        JSONObject flattenedParse = new JSONObject();
        flattenJson(inputParams, flattenedParse);
//                @requestParam Set<String> ids 这种是需要支持的
        Iterator<Map.Entry<String, Object>> iterator = inputParams.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            if (entry.getValue() instanceof JSONArray) {
                flattenedParse.put(entry.getKey(), StringUtils.join((JSONArray) entry.getValue(), ","));
                iterator.remove();
            }
        }

        if (!pathKeys.isEmpty()) {
            for (String path : pathKeys) {
                String val = null;
                try {
                    val = URLEncoder.encode(String.valueOf(flattenedParse.get(path)), "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                }
                flattenedParse.remove(path);

                if (aliasmap.containsKey(path)) {
                    path = aliasmap.get(path);
                }
                path1 = path1.replace("{" + path + "}", val);
//                        path 支持写类似正则的内容,类似/{userId1}/orders/{orderId:\\d+}
//                        请手动在 path1 中找{+path:的内容再找到下一个}进行补充替换
                // 处理带有正则的路径变量，例如：/{userId1}/orders/{orderId:\\d+}
                Pattern pattern = Pattern.compile("\\{" + Pattern.quote(path) + ":.*?\\}");
                Matcher matcher = pattern.matcher(path1);
                if (matcher.find()) {
                    path1 = matcher.replaceFirst(val);
                }
            }
        }

        Map<String, String> urlParams = new HashMap<>();
        if (!flattenedParse.isEmpty()) {
            Container<Boolean> first = new Container<>();
            first.set(true);
            flattenedParse.entrySet().forEach(new Consumer<Map.Entry<String, Object>>() {
                @Override
                public void accept(Map.Entry<String, Object> stringObjectEntry) {
                    String s = aliasmap.get(stringObjectEntry.getKey());
                    if (s != null && StringUtils.isBlank(s)) {
                        return;
                    }
                    s = s == null ? stringObjectEntry.getKey() : s;
                    urlParams.put(s, String.valueOf(stringObjectEntry.getValue()));
                }
            });
        }
        setOutputText("Generate controller command ...");
        try {
            String ret = invokeControllerScript(script, env, httpMethod, path1, urlParams, jsonBody, headerValues);
            setOutputText(ret);
        } catch (CompilationFailedException ex) {
            ex.printStackTrace();
            setOutputText("Command generate error\nPlease use the classes that come with jdk or groovy, do not use classes in your project\n" + ex.getClass().getSimpleName() + "\n" + ex.getMessage());
        } catch (Throwable ex) {
            ex.printStackTrace();
            setOutputText("Command generate error\n" + ex.getClass().getSimpleName() + "\n" + ex.getMessage());
        }
    }

    private void handleFeignCommand(String env, String script, PsiMethod method) {
        String jsonParams = jsonInputField.getText();
        JSONObject inputParams = null;
        try {
            inputParams = JSONObject.parseObject(jsonParams);
        } catch (Exception e) {
            setOutputText("Input parameter must be json object");
            return;
        }
        ReqStorageHelper.HttpCommandMeta commandMeta = parseFeignMeta(method);
        String httpMethod = commandMeta.getHttpMethod();
        String path1 = commandMeta.getPath();
        Map<String, String> aliasmap = commandMeta.getAliasmap();
        List<String> pathKeys = commandMeta.getPathKeys();
        String jsonBodyKey = commandMeta.getJsonBodyKey();
        Map<String, String> headers = commandMeta.getHeaders();
        Map<String, String> headerValues = new HashMap<>();

        String jsonBody = null;
        if (jsonBodyKey != null) {
            if (inputParams.get(jsonBodyKey) != null) {
                jsonBody = JSONObject.toJSONString(inputParams.get(jsonBodyKey), SerializerFeature.WriteMapNullValue);
            } else {
                jsonBody = "{}";
            }
            inputParams.remove(jsonBodyKey);
        }

        if (headers != null) {
            for (Map.Entry<String, String> stringEntry : headers.entrySet()) {
                String headerVal = inputParams.getString(stringEntry.getKey());
                inputParams.remove(stringEntry.getKey());
                if (headerVal == null) {
                    continue;
                }
                headerValues.put(stringEntry.getValue(), headerVal);
            }
        }


//               parse是个双层 map 参数
//                我想让双层key展开平铺
        JSONObject flattenedParse = new JSONObject();
        flattenJson(inputParams, flattenedParse);
//                @requestParam Set<String> ids 这种是需要支持的
        Iterator<Map.Entry<String, Object>> iterator = inputParams.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            if (entry.getValue() instanceof JSONArray) {
                flattenedParse.put(entry.getKey(), StringUtils.join((JSONArray) entry.getValue(), ","));
                iterator.remove();
            }
        }

        if (!pathKeys.isEmpty()) {
            for (String path : pathKeys) {
                String val = null;
                try {
                    val = URLEncoder.encode(String.valueOf(flattenedParse.get(path)), "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                }
                flattenedParse.remove(path);

                if (aliasmap.containsKey(path)) {
                    path = aliasmap.get(path);
                }
                path1 = path1.replace("{" + path + "}", val);
//                        path 支持写类似正则的内容,类似/{userId1}/orders/{orderId:\\d+}
//                        请手动在 path1 中找{+path:的内容再找到下一个}进行补充替换
                // 处理带有正则的路径变量，例如：/{userId1}/orders/{orderId:\\d+}
                Pattern pattern = Pattern.compile("\\{" + Pattern.quote(path) + ":.*?\\}");
                Matcher matcher = pattern.matcher(path1);
                if (matcher.find()) {
                    path1 = matcher.replaceFirst(val);
                }
            }
        }

        Map<String, String> urlParams = new HashMap<>();
        if (!flattenedParse.isEmpty()) {
            Container<Boolean> first = new Container<>();
            first.set(true);
            flattenedParse.entrySet().forEach(new Consumer<Map.Entry<String, Object>>() {
                @Override
                public void accept(Map.Entry<String, Object> stringObjectEntry) {
                    String s = aliasmap.get(stringObjectEntry.getKey());
                    if (s != null && StringUtils.isBlank(s)) {
                        return;
                    }
                    s = s == null ? stringObjectEntry.getKey() : s;
                    urlParams.put(s, String.valueOf(stringObjectEntry.getValue()));
                }
            });
        }
        setOutputText("Generate FeignClient command ...");
        try {
            String ret = invokeFeignScript(script, env, commandMeta.getFeignName(), commandMeta.getFeignUrl(), httpMethod, path1, urlParams, jsonBody, headerValues);
            setOutputText(ret);
        } catch (CompilationFailedException ex) {
            ex.printStackTrace();
            setOutputText("Command generate error\nPlease use the classes that come with jdk or groovy, do not use classes in your project\n" + ex.getClass().getSimpleName() + "\n" + ex.getMessage());
        } catch (Throwable ex) {
            ex.printStackTrace();
            setOutputText("Command generate error\n" + ex.getClass().getSimpleName() + "\n" + ex.getMessage());
        }
    }

    public String invokeFeignScript(String code, String env, String feignName, String feignUrl, String httpMethod, String path, Map<String, String> params, String jsonBody, Map<String, String> headerValues) {
        GroovyShell groovyShell = new GroovyShell();
        Script script = groovyShell.parse(code);
        Object build = InvokerHelper.invokeMethod(script, "generate", new Object[]{env, feignName, feignUrl, httpMethod, path, params, headerValues, jsonBody});
        return build == null ? "" : String.valueOf(build);
    }


    public String invokeControllerScript(String code, String env, String httpMethod, String path, Map<String, String> params, String jsonBody, Map<String, String> headerValues) {
        RuntimeHelper.VisibleApp selectedApp = RuntimeHelper.getSelectedApp(getProject().getName());
        GroovyShell groovyShell = new GroovyShell();
        Script script = groovyShell.parse(code);
        Object build = InvokerHelper.invokeMethod(script, "generate", new Object[]{env, selectedApp == null ? null : selectedApp.buildWebPort(), httpMethod, path, params, headerValues, jsonBody});
        return build == null ? "" : String.valueOf(build);
    }


    protected JPanel createActionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        actionComboBox = addActionComboBox(FunctionCallIconProvider.FUNCTION_CALL_ICON, FUNCTION_CALL_DISABLE_ICON,
                "<strong>function-call</strong>\n<ul>\n" +
                        "    <li>spring bean method</li>\n" +
                        "    <li>not main/bean method</li>\n" +
                        "    <li>not in test source</li>\n" +
                        "</ul>", panel, new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        triggerChangeAction();
                    }
                });
        // Populate methodComboBox with method names


        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(1);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 3;
        gbc.gridy = 0;

        // Add the radio button
        useProxyButton = new JToggleButton(PROXY_ICON, true);
        useProxyButton.setPreferredSize(new Dimension(32, 32));
        useProxyButton.setToolTipText("Use proxy obj call function");
        useProxyButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (useProxyButton.isSelected()) {
                    useProxyButton.setIcon(PROXY_ICON);
                    useProxyButton.setToolTipText("Use proxy obj call function");
                } else {
                    useProxyButton.setIcon(PROXY_DISABLE_ICON);
                    useProxyButton.setToolTipText("Use original obj call function");
                }
            }
        });
        panel.add(useProxyButton, gbc);

        runButton = new JButton(AllIcons.Actions.Execute);
        runButton.setToolTipText("Execute this");
        //        // 设置按钮大小
        Dimension buttonSize = new Dimension(32, 32);
        runButton.setPreferredSize(buttonSize);

        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                RuntimeHelper.VisibleApp app = RuntimeHelper.getSelectedApp(getProject().getName());
                if (app == null) {
                    Messages.showMessageDialog(getProject(),
                            "Failed to find runtime app",
                            "Error",
                            Messages.getErrorIcon());
                    return;
                }
                triggerHttpTask(runButton, AllIcons.Actions.Execute, app.getTestkitPort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        String jsonInput = jsonInputField.getText();
                        if (jsonInput == null || jsonInput.isBlank()) {
                            setOutputText("input parameter is blank");
                            return null;
                        }
                        JSONObject jsonObject;
                        try {
                            jsonObject = JSONObject.parseObject(jsonInput);
                        } catch (Exception ex) {
                            setOutputText("input parameter must be json object");
                            return null;
                        }
                        ToolHelper.MethodAction selectedItem = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
                        if (selectedItem == null) {
                            TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Please select method");
                            return null;
                        }
                        selectedItem.setArgs(jsonInput);
                        return buildParams(selectedItem.getMethod(), jsonObject, PluginToolEnum.FUNCTION_CALL.getCode());
                    }
                });
            }
        });

        gbc.gridx = 4;
        panel.add(runButton, gbc);

        return panel;
    }

    @Override
    protected void handleCopyInput(AnActionEvent e) {
        String jsonInput = jsonInputField.getText();
        if (jsonInput == null || jsonInput.isBlank()) {
            TestkitHelper.notify(getProject(), NotificationType.ERROR, "input parameter is blank");
            return;
        }
        JSONObject jsonObject;
        try {
            jsonObject = JSONObject.parseObject(jsonInput);
        } catch (Exception ex) {
            TestkitHelper.notify(getProject(), NotificationType.ERROR, "input parameter must be json object");
            return;
        }
        ToolHelper.MethodAction selectedItem = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
        if (selectedItem == null) {
            TestkitHelper.notify(getProject(), NotificationType.ERROR, "Please select method");
            return;
        }

        //新增drop down
        DefaultActionGroup copyGroup = new DefaultActionGroup();
        //显示的一个图标加上标题
        AnAction copyDirect = new AnAction("Copy the input params", "Copy the input params", JSON_ICON) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                FunctionCallTool.super.handleCopyInput(e);
            }
        };
        copyGroup.add(copyDirect); // 将动作添加到动作组中
        PsiMethod method = selectedItem.getMethod();


        Map<String, String> appInterceptors = RuntimeHelper.getAppMetas(toolWindow.getProject().getName()).stream().filter(new Predicate<RuntimeHelper.AppMeta>() {
            @Override
            public boolean test(RuntimeHelper.AppMeta appMeta) {
                return ToolHelper.isDependency(selectedItem.getMethod(), getProject(), appMeta.getModule());
            }
        }).collect(Collectors.toMap(new Function<RuntimeHelper.AppMeta, String>() {
            @Override
            public String apply(RuntimeHelper.AppMeta appMeta) {
                return appMeta.getApp();
            }
        }, new Function<RuntimeHelper.AppMeta, String>() {
            @Override
            public String apply(RuntimeHelper.AppMeta appMeta) {
                String s = SettingsStorageHelper.encodeInterceptor(toolWindow.getProject(), appMeta.getApp());
                return s == null ? "" : s;
            }
        }));


        if (useInterceptor && !appInterceptors.isEmpty()) {
            boolean canInitparams = FunctionCallIconProvider.canInitparams(method);
            boolean springCacheMethod = FunctionCallIconProvider.isSpringCacheMethod(method);
            for (Map.Entry<String, String> stringStringEntry : appInterceptors.entrySet()) {
                if (canInitparams) {
                    AnAction copyCmd = new AnAction("Copy Function-call " + stringStringEntry.getKey() + " Cmd", "Copy Function-call " + stringStringEntry.getKey() + " Cmd", CMD_ICON) {
                        @Override
                        public void actionPerformed(@NotNull AnActionEvent e) {
                            JSONObject callReq = buildParams(selectedItem.getMethod(), jsonObject, PluginToolEnum.FUNCTION_CALL.getCode());
                            callReq.put("interceptor", "".equals(stringStringEntry.getValue()) ? null : stringStringEntry.getValue());
                            String cmd = PluginToolEnum.FUNCTION_CALL.getCode() + " " + JSONObject.toJSONString(callReq);
                            TestkitHelper.copyToClipboard(getProject(), cmd, "Cmd copied<br>You can execute this directly in testkit-dig");
                        }
                    };
                    copyGroup.add(copyCmd); // 将动作添加到动作组中
                }

                if (springCacheMethod) {
                    HashMap<String, String> map = new HashMap<>();
                    map.put("Build-key", "build_cache_key");
                    map.put("Build-key&Get-value", "get_cache");
                    map.put("Build-key&Del-value", "delete_cache");
                    for (String cacheAction : Arrays.asList("Build-key", "Build-key&Get-value", "Build-key&Del-value")) {
                        AnAction copyCmd = new AnAction("Copy " + cacheAction + " " + stringStringEntry.getKey() + " Cmd", "Copy " + cacheAction + " " + stringStringEntry.getKey() + " Cmd", CMD_ICON) {
                            @Override
                            public void actionPerformed(@NotNull AnActionEvent e) {
                                JSONObject callReq = buildCacheParams(method, jsonObject, map.get(cacheAction));
                                callReq.put("interceptor", "".equals(stringStringEntry.getValue()) ? null : stringStringEntry.getValue());
                                String cms = "spring-cache " + JSONObject.toJSONString(callReq);
                                TestkitHelper.copyToClipboard(getProject(), cms, "Cmd copied<br>You can execute this directly in testkit-dig");
                            }
                        };
                        copyGroup.add(copyCmd); // 将动作添加到动作组中
                    }
                }


            }
        } else {
            //如果是普通的函数，则
            if (FunctionCallIconProvider.canInitparams(method)) {
                //找出来app及对应的脚本
                AnAction copyCmd = new AnAction("Copy Function-call Cmd", "Copy Function-call Cmd", CMD_ICON) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        JSONObject callReq = buildParams(method, jsonObject, PluginToolEnum.FUNCTION_CALL.getCode());
                        callReq.put("interceptor", null);
                        String cms = PluginToolEnum.FUNCTION_CALL.getCode() + " " + JSONObject.toJSONString(callReq);
                        TestkitHelper.copyToClipboard(getProject(), cms, "Cmd copied<br>You can execute this directly in testkit-dig");
                    }
                };
                copyGroup.add(copyCmd); // 将动作添加到动作组中
            }


            if (FunctionCallIconProvider.isSpringCacheMethod(method)) {
                HashMap<String, String> map = new HashMap<>();
                map.put("Build-key", "build_cache_key");
                map.put("Build-key&Get-value", "get_cache");
                map.put("Build-key&Del-value", "delete_cache");
                for (String cacheAction : Arrays.asList("Build-key", "Build-key&Get-value", "Build-key&Del-value")) {
                    AnAction copyCmd = new AnAction("Copy " + cacheAction + " Cmd", "Copy " + cacheAction + " Cmd", CMD_ICON) {
                        @Override
                        public void actionPerformed(@NotNull AnActionEvent e) {
                            JSONObject callReq = buildCacheParams(method, jsonObject, map.get(cacheAction));
                            callReq.put("interceptor", null);
                            String cms = "spring-cache " + JSONObject.toJSONString(callReq);
                            TestkitHelper.copyToClipboard(getProject(), cms, "Cmd copied<br>You can execute this directly in testkit-dig");
                        }
                    };
                    copyGroup.add(copyCmd); // 将动作添加到动作组中
                }
            }
        }

        JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("CopyFunctionCallPopup", copyGroup).getComponent();
        popupMenu.show(jsonInputField, 0, 0);
    }


    private JSONObject buildParams(PsiMethod method, JSONObject args, String action) {
        JSONObject params = new JSONObject();
        PsiClass containingClass = method.getContainingClass();
        String typeClass = containingClass.getQualifiedName();
        params.put("typeClass", typeClass);

        String beanName = ToolHelper.getBeanNameFromClass(containingClass);
        params.put("beanName", beanName);
        params.put("methodName", method.getName());

        PsiParameter[] parameters = method.getParameterList().getParameters();
        String[] argTypes = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            argTypes[i] = parameters[i].getType().getCanonicalText();
        }
        params.put("argTypes", JSONObject.toJSONString(argTypes));
        params.put("args", ToolHelper.adapterParams(method, args).toJSONString());
        params.put("original", !useProxyButton.isSelected());
        JSONObject req = new JSONObject();
        req.put("method", action);
        req.put("params", params);

        SettingsStorageHelper.TraceConfig traceConfig = SettingsStorageHelper.getTraceConfig(getProject());
        req.put("trace", traceConfig.isEnable());
//        req.put("singleClsDepth", traceConfig.getSingleClsDepth());
        if (useInterceptor) {
            RuntimeHelper.VisibleApp visibleApp = RuntimeHelper.getSelectedApp(getProject().getName());
            req.put("interceptor", SettingsStorageHelper.encodeInterceptor(getProject(), visibleApp == null ? null : visibleApp.getAppName()));
        }
        return req;
    }


    private JSONObject handleCacheAction(String action) {
        String jsonInput = jsonInputField.getText();
        if (jsonInput == null || jsonInput.isBlank()) {
            setOutputText("input parameter is blank");
            return null;
        }
        JSONObject jsonObject;
        try {
            jsonObject = JSONObject.parseObject(jsonInput);
        } catch (Exception ex) {
            setOutputText("input parameter must be json obj");
            return null;
        }
        ToolHelper.MethodAction selectedItem = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
        if (selectedItem == null) {
            TestkitHelper.alert(getProject(), Messages.getErrorIcon(), "Please select method");
            return null;
        }
        selectedItem.setArgs(jsonInput);
        return buildCacheParams(selectedItem.getMethod(), jsonObject, action);
    }

    private JSONObject buildCacheParams(PsiMethod method, JSONObject args, String action) {
        JSONObject params = new JSONObject();
        PsiClass containingClass = method.getContainingClass();
        String typeClass = containingClass.getQualifiedName();
        params.put("typeClass", typeClass);

        String beanName = ToolHelper.getBeanNameFromClass(containingClass);
        params.put("beanName", beanName);
        params.put("methodName", method.getName());

        PsiParameter[] parameters = method.getParameterList().getParameters();
        String[] argTypes = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            argTypes[i] = parameters[i].getType().getCanonicalText();
        }
        params.put("argTypes", JSONObject.toJSONString(argTypes));
        params.put("args", ToolHelper.adapterParams(method, args).toJSONString());
        params.put("action", action);
        JSONObject req = new JSONObject();
        req.put("method", "spring-cache");
        req.put("params", params);
        if (useInterceptor) {
            RuntimeHelper.VisibleApp visibleApp = RuntimeHelper.getSelectedApp(getProject().getName());
            req.put("interceptor", SettingsStorageHelper.encodeInterceptor(getProject(), visibleApp == null ? null : visibleApp.getAppName()));
        }
        SettingsStorageHelper.TraceConfig traceConfig = SettingsStorageHelper.getTraceConfig(getProject());
        req.put("trace", traceConfig.isEnable());
//        req.put("singleClsDepth", traceConfig.getSingleClsDepth());
        return req;
    }

    @Override
    public boolean needAppBox() {
        return true;
    }

    @Override
    public void onSwitchAction(PsiElement psiElement) {
        if (!(psiElement instanceof PsiMethod)) {
            Messages.showMessageDialog(getProject(),
                    "un support element",
                    "Error",
                    Messages.getErrorIcon());
            return;
        }
        PsiMethod method = (PsiMethod) psiElement;
        ToolHelper.MethodAction methodAction = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
        if (methodAction != null && methodAction.getMethod().equals(method)) {
            return;
        }

        // 检查下拉框中是否已经包含当前方法的名称
        for (int i = 0; i < actionComboBox.getItemCount(); i++) {
            if (actionComboBox.getItemAt(i).toString().equals(ToolHelper.buildMethodKey(method))) {
                actionComboBox.setSelectedIndex(i);
                return;
            }
        }
        // 如果当前下拉框没有此选项，则新增该选项
        ToolHelper.MethodAction item = new ToolHelper.MethodAction(method);
        actionComboBox.addItem(item);
        actionComboBox.setSelectedItem(item);
    }

    private void triggerChangeAction() {
        // 获取当前选中的值
        ToolHelper.MethodAction methodAction = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
        if (methodAction == null || methodAction.getMethod() == null || !methodAction.getMethod().isValid()) {
            jsonInputField.setText("{}");
            return;
        }

        PsiMethod method = methodAction.getMethod();
        if (!FunctionCallIconProvider.canInitparams(method)) {
            //如果存在接口或者超类无法明确类那就设置按钮
            // 新增：检查所有参数都可以直接序列化
            runButton.setIcon(AllIcons.Hierarchy.MethodNotDefined);
            runButton.setToolTipText("Un support execute");
        } else {
            runButton.setIcon(AllIcons.Actions.Execute);
            runButton.setToolTipText("Execute this");
        }

        //判断是http 还是 feign
        if (FunctionCallIconProvider.isSpringCacheMethod(method)) {
            List<Component> components = Arrays.asList(actionPanel.getComponents());
            if (!components.contains(getKeyButton)) {
                actionPanel.add(getKeyButton);
            }
            if (!components.contains(getValButton)) {
                actionPanel.add(getValButton);
            }
            if (!components.contains(delValButton)) {
                actionPanel.add(delValButton);
            }
        } else {
            //判断panel1是否存在button1
            List<Component> components = Arrays.asList(actionPanel.getComponents());
            if (components.contains(getKeyButton)) {
                actionPanel.remove(getKeyButton);
            }
            if (components.contains(getValButton)) {
                actionPanel.remove(getValButton);
            }
            if (components.contains(delValButton)) {
                actionPanel.remove(delValButton);
            }
        }

        boolean requestMethod = FunctionCallIconProvider.isRequestMethod(method);

        //判断是http 还是 feign
        if (FunctionCallIconProvider.isFeign(method)) {
            List<Component> components = Arrays.asList(actionPanel.getComponents());
            if (components.contains(controllerCommandButton)) {
                actionPanel.remove(controllerCommandButton);
            }
            if (requestMethod && !components.contains(feignCommandButton)) {
                actionPanel.add(feignCommandButton);
            } else if (!requestMethod && components.contains(feignCommandButton)) {
                actionPanel.remove(feignCommandButton);
            }
        } else {
            //判断panel1是否存在button1
            List<Component> components = Arrays.asList(actionPanel.getComponents());
            if (components.contains(feignCommandButton)) {
                actionPanel.remove(feignCommandButton);
            }
            if (requestMethod && !components.contains(controllerCommandButton)) {
                actionPanel.add(controllerCommandButton);
            } else if (!requestMethod && components.contains(controllerCommandButton)) {
                actionPanel.remove(controllerCommandButton);
            }
        }

        ToolHelper.initParamsTextField(jsonInputField, methodAction);
    }


    public static void flattenJson(JSONObject parse, JSONObject flattenedParse) {
        for (String key : parse.keySet()) {
            Object value = parse.get(key);

            if (value == null) {
                continue;
            }

            // 检查是否为原始类型
            if (value.getClass().isPrimitive() || value instanceof String
                    || value instanceof Boolean
                    || value instanceof Number
                    || value instanceof Double
                    || value instanceof Float
                    || value instanceof Long
                    || value instanceof Short
                    || value instanceof Character
                    || value instanceof Byte
                    || value instanceof BigDecimal
                    || value instanceof BigInteger) {
                flattenedParse.put(key, value);
            } else if (value instanceof JSONObject) {
                // 递归展平 JSONObject，并直接将结果放入 flattenedParse
                flattenJson((JSONObject) value, flattenedParse);
            }
        }
    }

    @Override
    protected boolean hasActionBox() {
        return actionComboBox != null;
    }

    @Override
    protected void refreshInputByActionBox() {
        refreshInputByActionBox(actionComboBox);
    }
}
