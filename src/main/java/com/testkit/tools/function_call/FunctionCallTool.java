package com.testkit.tools.function_call;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.intellij.psi.util.PsiUtil;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FunctionCallTool extends BasePluginTool {

    public static final Icon FUNCTION_CALL_DISABLE_ICON = IconLoader.getIcon("/icons/function-call-disable.svg", FunctionCallTool.class);

    public static final Icon PROXY_DISABLE_ICON = IconLoader.getIcon("/icons/proxy-disable.svg", FunctionCallTool.class);
    public static final Icon PROXY_ICON = IconLoader.getIcon("/icons/proxy.svg", FunctionCallTool.class);
    public static final Icon CONTROLLER_ICON = IconLoader.getIcon("/icons/controller.svg", FunctionCallTool.class);
    public static final Icon GENERATE_ICON = IconLoader.getIcon("/icons/generate.svg", FunctionCallTool.class);


    private JButton controllerCommandButton;
    private JButton runButton;
    private JComboBox<ToolHelper.MethodAction> actionComboBox;

    private JToggleButton useProxyButton;

    {
        this.tool = PluginToolEnum.FUNCTION_CALL;
    }

    public FunctionCallTool(TestkitToolWindow testkitToolWindow) {
        super(testkitToolWindow);

        controllerCommandButton = new JButton(CONTROLLER_ICON);
        controllerCommandButton.setPreferredSize(new Dimension(32, 32));
        controllerCommandButton.setToolTipText("Generate controller command");
        controllerCommandButton.addActionListener(e -> {
            ToolHelper.MethodAction selectedItem = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
            if (selectedItem == null) {
                TestkitHelper.alert(getProject(),Messages.getErrorIcon(),"Please select a method");
                return;
            }
            List<String> projectAppList = RuntimeHelper.getAppMetas(toolWindow.getProject().getName()).stream().filter(new Predicate<RuntimeHelper.AppMeta>() {
                        @Override
                        public boolean test(RuntimeHelper.AppMeta appMeta) {
                            return ToolHelper.isDependency(selectedItem.getMethod(),getProject(),appMeta.getModule());
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
                    SettingsStorageHelper.ControllerCommand controllerCommand = SettingsStorageHelper.getAppControllerCommand(testkitToolWindow.getProject(), app);
                    String script = controllerCommand.getScript();
                    List<String> envs = controllerCommand.getEnvs();
                    if (CollectionUtils.isEmpty(envs) && Objects.equals(script, SettingsStorageHelper.DEF_CONTROLLER_COMMAND.getScript())) {
                        continue;
                    }
                    if(CollectionUtils.isEmpty(envs)){
                        envs = new ArrayList<>();
                        envs.add(null);
                    }
                    for (String env : envs) {
                        //显示的一个图标加上标题
                        AnAction documentation = new AnAction("Generate with " + app + ":" + env, "Generate with " + app + ":" + env, GENERATE_ICON) {
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


    protected boolean canStore() {
        return true;
    }

    protected List<String> verifyNowStore() {
        ToolHelper.MethodAction methodAction = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
        if (methodAction == null || !methodAction.getMethod().isValid()) {
            throw new RuntimeException("Please select a valid method");
        }
        try {
            JSONObject.parseObject(inputEditorTextField.getText().trim());
        } catch (Exception e) {
            throw new RuntimeException("Input parameter must be json object");
        }
        return RuntimeHelper.getAppMetas(toolWindow.getProject().getName()).stream().filter(new Predicate<RuntimeHelper.AppMeta>() {
            @Override
            public boolean test(RuntimeHelper.AppMeta appMeta) {
                return ToolHelper.isDependency(methodAction.getMethod(),getProject(),appMeta.getModule());
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
        String text = inputEditorTextField.getText();
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

        ReqStorageHelper.CallMethodMeta methodMeta = new ReqStorageHelper.CallMethodMeta();
        methodMeta.setTypeClass(typeClass);
        methodMeta.setBeanName(beanName);
        methodMeta.setMethodName(method.getName());
        methodMeta.setArgNames(argNames);
        methodMeta.setArgTypes(JSONObject.toJSONString(argTypes));
        methodMeta.setUseInterceptor(useInterceptor);
        ReqStorageHelper.SubItemType subItemType = null;
        if (Arrays.asList(actionPanel.getComponents()).contains(controllerCommandButton)) {
            subItemType = ReqStorageHelper.SubItemType.controller;
            methodMeta.setControllerMeta(parseControllerMeta(method));
        }
        methodMeta.setSubType(subItemType);
        ReqStorageHelper.saveAppReq(getProject(), app, StringUtils.isBlank(group) ? "undefined" : group, ReqStorageHelper.ItemType.function_call, ToolHelper.buildMethodKey(method), methodMeta, savedReq.getTitle(), savedReq);
    }



    public ReqStorageHelper.ControllerCommandMeta parseControllerMeta(PsiMethod method) {
        ReqStorageHelper.ControllerCommandMeta commandMeta = new ReqStorageHelper.ControllerCommandMeta();

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


            PsiAnnotationMemberValue methodValue = methodAnnotation.findAttributeValue("value");
            if (methodValue == null || (methodValue instanceof PsiLiteralExpression && StringUtils.isBlank(String.valueOf(((PsiLiteralExpression) methodValue).getValue())))) {
                methodValue = methodAnnotation.findAttributeValue("path");
            }
            if (StringUtils.isNotBlank(reqPath)) {
                if (path1.endsWith("/")) {
                    if (reqPath.startsWith("/")) {
                        path1 += reqPath.substring(1);
                    } else {
                        path1 += methodValue.getText();
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
        for (PsiParameter parameter : parameters) {
            PsiAnnotation requestBodyAnnotation = parameter.getModifierList().findAnnotation("org.springframework.web.bind.annotation.RequestBody");
            if (requestBodyAnnotation != null) {
                if (httpMethod.equals("GET")) {
                    httpMethod = "POST";
                }
                jsonBodyKey = parameter.getName();
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
            PsiAnnotationMemberValue requestParamValue = requestParamAnnotation.findAttributeValue("value");
            if (requestParamValue == null || (requestParamValue instanceof PsiLiteralExpression && StringUtils.isBlank(String.valueOf(((PsiLiteralExpression) requestParamValue).getValue())))) {
                requestParamValue = requestParamAnnotation.findAttributeValue("name");
            }
            if (requestParamValue instanceof PsiLiteralExpression) {
                String cs = String.valueOf(((PsiLiteralExpression) requestParamValue).getValue());
                if (StringUtils.isNotBlank(cs)) {
                    // 将别名注册到 aliasMap 中
                    aliasmap.put(parameter.getName(), cs);
                }
            }
        }

        commandMeta.setHttpMethod(httpMethod);
        commandMeta.setPath(path1);
        commandMeta.setAliasmap(aliasmap);
        commandMeta.setJsonBodyKey(jsonBodyKey);
        commandMeta.setPathKeys(paths);
        return commandMeta;
    }

    private void handleControllerCommand(String env, String script, PsiMethod method) {
        String jsonParams = inputEditorTextField.getText();
        JSONObject inputParams = null;
        try {
            inputParams = JSONObject.parseObject(jsonParams);
        } catch (Exception e) {
            setOutputText("Input parameter must be json object");
            return;
        }
        ReqStorageHelper.ControllerCommandMeta commandMeta = parseControllerMeta(method);
        String httpMethod = commandMeta.getHttpMethod();
        String path1 = commandMeta.getPath();
        Map<String, String> aliasmap = commandMeta.getAliasmap();
        List<String> pathKeys = commandMeta.getPathKeys();
        String jsonBodyKey = commandMeta.getJsonBodyKey();


        String jsonBody = null;
        if (jsonBodyKey != null) {
            if (inputParams.get(jsonBodyKey) != null) {
                jsonBody = JSONObject.toJSONString(inputParams.get(jsonBodyKey), SerializerFeature.WriteMapNullValue);
            } else {
                jsonBody = "{}";
            }
            inputParams.remove(jsonBodyKey);
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
            String ret = invokeControllerScript(script, env, httpMethod, path1, urlParams, jsonBody);
            setOutputText(ret);
        } catch (CompilationFailedException ex) {
            ex.printStackTrace();
            setOutputText("Command generate error, Please use the classes that come with jdk or groovy, do not use classes in your project, " + ex.getClass().getSimpleName() + ", " + ex.getMessage());
        } catch (Throwable ex) {
            ex.printStackTrace();
            setOutputText("Command generate error," + ex.getClass().getSimpleName() + ", " + ex.getMessage());
        }
    }


    public String invokeControllerScript(String code, String env, String httpMethod, String path, Map<String, String> params, String jsonBody) {
        RuntimeHelper.VisibleApp selectedApp = RuntimeHelper.getSelectedApp(getProject().getName());
        GroovyShell groovyShell = new GroovyShell();
        Script script = groovyShell.parse(code);
        Object build = InvokerHelper.invokeMethod(script, "generate", new Object[]{env, selectedApp == null ? null : selectedApp.getPort(), httpMethod, path, params, jsonBody});
        return build == null ? "" : String.valueOf(build);
    }


    protected JPanel createActionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        actionComboBox = addActionComboBox(FunctionCallIconProvider.FUNCTION_CALL_ICON, FUNCTION_CALL_DISABLE_ICON,
                "<strong>function-call</strong>\n<ul>\n" +
                        "    <li>spring bean 的 public 函数</li>\n" +
                        "    <li>非init/main</li>\n" +
                        "    <li>非test source</li>\n" +
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
                triggerHttpTask(runButton, AllIcons.Actions.Execute, app.getSidePort(), new Supplier<JSONObject>() {
                    @Override
                    public JSONObject get() {
                        String jsonInput = inputEditorTextField.getDocument().getText();
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
                            TestkitHelper.alert(getProject(),Messages.getErrorIcon(),"Please select method");
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
        req.put("singleClsDepth", traceConfig.getSingleClsDepth());
        if (useInterceptor) {
            RuntimeHelper.VisibleApp visibleApp = RuntimeHelper.getSelectedApp(getProject().getName());
            req.put("interceptor", SettingsStorageHelper.getAppScript(getProject(), visibleApp==null?null:visibleApp.getAppName()));
        }
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
            inputEditorTextField.setText("{}");
            return;
        }

        PsiMethod method = methodAction.getMethod();
        boolean requestMethod = FunctionCallIconProvider.isRequestMethod(method);
        if(requestMethod && !FunctionCallIconProvider.canInitparams(method)){
            //如果存在接口或者超类无法明确类那就设置按钮
            // 新增：检查所有参数都可以直接序列化
            runButton.setIcon(AllIcons.Hierarchy.MethodNotDefined);
            runButton.setToolTipText("Can not support execute");
        }else{
            runButton.setIcon(AllIcons.Actions.Execute);
            runButton.setToolTipText("Execute this");
        }

        //判断panel1是否存在button1
        if (requestMethod && !Arrays.asList(actionPanel.getComponents()).contains(controllerCommandButton)) {
            actionPanel.add(controllerCommandButton);
        } else if (!requestMethod && Arrays.asList(actionPanel.getComponents()).contains(controllerCommandButton)) {
            actionPanel.remove(controllerCommandButton);
        }
        ToolHelper.initParamsTextField(inputEditorTextField, methodAction);
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
