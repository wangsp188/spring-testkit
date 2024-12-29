package com.fling.tools.call_method;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fling.FlingHelper;
import com.fling.ReqStorageHelper;
import com.fling.tools.ToolHelper;
import com.fling.util.Container;
import com.fling.view.FlingToolWindow;
import com.intellij.icons.AllIcons;
import com.fling.util.HttpUtil;
import com.fling.LocalStorageHelper;
import com.intellij.notification.NotificationType;
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
import com.fling.tools.BasePluginTool;
import com.fling.tools.PluginToolEnum;
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
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CallMethodTool extends BasePluginTool {

    public static final Icon CALL_METHOD_DISABLE_ICON = IconLoader.getIcon("/icons/spring-fling-disable.svg", CallMethodTool.class);

    public static final Icon PROXY_DISABLE_ICON = IconLoader.getIcon("/icons/proxy-disable.svg", CallMethodTool.class);
    public static final Icon PROXY_ICON = IconLoader.getIcon("/icons/proxy.svg", CallMethodTool.class);
    public static final Icon CONTROLLER_ICON = IconLoader.getIcon("/icons/controller.svg", CallMethodTool.class);
    public static final Icon GENERATE_ICON = IconLoader.getIcon("/icons/generate.svg", CallMethodTool.class);


    private JComboBox<ToolHelper.MethodAction> actionComboBox;

    private JToggleButton useProxyButton;

    protected AnAction copyCurlAction;

    {
        this.tool = PluginToolEnum.CALL_METHOD;
    }

    public CallMethodTool(FlingToolWindow flingToolWindow) {
        super(flingToolWindow);

        AnAction storeAction = new AnAction("Save this req", "Save this req", AllIcons.Actions.MenuSaveall) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                List<String> projectAppList = flingToolWindow.getProjectAppList();
                if (projectAppList.isEmpty()) {
                    FlingHelper.alert(getProject(), Messages.getErrorIcon(), "Can not find app");
                    return;
                }

                // 调用复制功能
                ToolHelper.MethodAction methodAction = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
                if (methodAction == null || !methodAction.getMethod().isValid()) {
                    FlingHelper.alert(getProject(), Messages.getErrorIcon(), "Please select a valid method");
                    return;
                }

                String app = projectAppList.get(0);

                String text = inputEditorTextField.getText();
                JSONObject args = null;
                try {
                    args = JSONObject.parseObject(text);
                } catch (Exception ex) {
                    FlingHelper.alert(getProject(), Messages.getErrorIcon(), "Input parameter must be json object");
                    return;
                }


                PsiMethod method = methodAction.getMethod();
                PsiClass containingClass = method.getContainingClass();
                String typeClass = containingClass.getQualifiedName();
                String beanName = ToolHelper.getBeanNameFromClass(containingClass);

                PsiParameter[] parameters = method.getParameterList().getParameters();
                String[] argTypes = new String[parameters.length];
                for (int i = 0; i < parameters.length; i++) {
                    argTypes[i] = parameters[i].getType().getCanonicalText();
                }
                JSONArray storeArgs = ToolHelper.adapterParams(method, args);

                ReqStorageHelper.SavedReq savedReq = new ReqStorageHelper.SavedReq();
                savedReq.setTitle("default");
                ReqStorageHelper.CallMethodMeta methodMeta = new ReqStorageHelper.CallMethodMeta();
                methodMeta.setArgs(storeArgs);
                methodMeta.setTypeClass(typeClass);
                methodMeta.setBeanName(beanName);
                methodMeta.setMethodName(method.getName());
                methodMeta.setArgTypes(JSONObject.toJSONString(argTypes));
                methodMeta.setOriginal(!useProxyButton.isSelected());
                savedReq.setMeta(JSONObject.parseObject(JSONArray.toJSONString(methodMeta)));

                ReqStorageHelper.saveAppReq(getProject(), app, "default",ReqStorageHelper.ItemType.call_method,ToolHelper.buildMethodKey(method),savedReq);
                FlingHelper.notify(getProject(), NotificationType.INFORMATION, "Req already Saved");
            }
        };
        actionGroup.add(storeAction);


        AnAction historyAction = new AnAction("Open Store", "Open Store", AllIcons.Vcs.History) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                // 调用复制功能
                flingWindow.visibleStoreDialog();
            }
        };
        actionGroup.add(historyAction);

        copyCurlAction = new AnAction("Controller Command", "Controller Command", CONTROLLER_ICON) {

            @Override
            public void actionPerformed(AnActionEvent e) {
                ToolHelper.MethodAction selectedItem = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
                if (selectedItem == null) {
                    return;
                }
                List<String> porjectAppList = getPorjectAppList();

                DefaultActionGroup controllerActionGroup = new DefaultActionGroup();
                if (CollectionUtils.isNotEmpty(porjectAppList)) {
                    for (String app : porjectAppList) {
                        LocalStorageHelper.ControllerCommand controllerCommand = LocalStorageHelper.getAppControllerCommand(flingToolWindow.getProject(), app);
                        String script = controllerCommand.getScript();
                        List<String> envs = controllerCommand.getEnvs();
                        if (CollectionUtils.isEmpty(envs)) {
                            continue;
                        }
                        for (String env : envs) {
                            if (env == null) {
                                continue;
                            }
                            //显示的一个图标加上标题
                            AnAction documentation = new AnAction("generate with " + app + ":" + env, "generate with " + app + ":" + env, GENERATE_ICON) {
                                @Override
                                public void actionPerformed(@NotNull AnActionEvent e) {
                                    Application application = ApplicationManager.getApplication();
                                    ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), "Processing generate function, please wait...", false) {

                                        @Override
                                        public void run(@NotNull ProgressIndicator indicator) {
                                            application.runReadAction(new Runnable() {
                                                @Override
                                                public void run() {
                                                    handleControllerAdapter(env, script, selectedItem.getMethod());
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
                    handleControllerAdapter(null, LocalStorageHelper.DEF_CONTROLLER_COMMAND.getScript(), selectedItem.getMethod());
                    return;
                }

                JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("ControllerAdapterPopup", controllerActionGroup).getComponent();
                popupMenu.show(inputEditorTextField, 0, 90);
            }

        };
    }

    private void handleControllerAdapter(String env, String script, PsiMethod method) {
        String jsonParams = inputEditorTextField.getText();
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
        JSONObject parse = null;
        try {
            parse = JSONObject.parseObject(jsonParams);
        } catch (Exception e) {
            FlingHelper.alert(getProject(), Messages.getErrorIcon(), "Input parameter must be json object");
            return;
        }
        ArrayList<String> paths = new ArrayList<>();
        Map<String, String> aliasmap = new HashMap<>();
        String requestBodyKey = null;
        for (PsiParameter parameter : parameters) {
            PsiAnnotation requestBodyAnnotation = parameter.getModifierList().findAnnotation("org.springframework.web.bind.annotation.RequestBody");
            if (requestBodyAnnotation != null) {
                if (httpMethod.equals("GET")) {
                    httpMethod = "POST";
                }
                requestBodyKey = parameter.getName();
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

        String jsonBody = null;
        if (requestBodyKey != null) {
            if (parse.get(requestBodyKey) != null) {
                jsonBody = JSONObject.toJSONString(parse.get(requestBodyKey), SerializerFeature.WriteMapNullValue);
            } else {
                jsonBody = "{}";
            }
            parse.remove(requestBodyKey);
        }


//               parse是个双层 map 参数
//                我想让双层key展开平铺
        JSONObject flattenedParse = new JSONObject();
        flattenJson(parse, flattenedParse);
//                @requestParam Set<String> ids 这种是需要支持的
        Iterator<Map.Entry<String, Object>> iterator = parse.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            if (entry.getValue() instanceof JSONArray) {
                flattenedParse.put(entry.getKey(), StringUtils.join((JSONArray) entry.getValue(), ","));
                iterator.remove();
            }
        }

        if (!paths.isEmpty()) {
            for (String path : paths) {
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
        try {
            String ret = invokeControllerScript(script, env, httpMethod, path1, urlParams, jsonBody);
            if (env == null || Objects.equals(LocalStorageHelper.DEF_CONTROLLER_COMMAND.getScript(), script)) {
                FlingHelper.copyToClipboard(getProject(), ret, "Curl was copied to the clipboard, please replace it with the real authentication token.");
            } else {
                FlingHelper.copyToClipboard(getProject(), ret, "Result was copied to the clipboard.");
            }
        } catch (CompilationFailedException ex) {
            ex.printStackTrace();
            FlingHelper.notify(getProject(), NotificationType.ERROR, "Command generate error, Please use the classes that come with jdk or groovy, do not use classes in your project, " + ex.getClass().getSimpleName() + ", " + ex.getMessage());
        } catch (Throwable ex) {
            ex.printStackTrace();
            FlingHelper.notify(getProject(), NotificationType.ERROR, "Command generate error," + ex.getClass().getSimpleName() + ", " + ex.getMessage());
        }
    }


    public String invokeControllerScript(String code, String env, String httpMethod, String path, Map<String, String> params, String jsonBody) {
        FlingToolWindow.VisibleApp selectedApp = getSelectedApp();
        GroovyShell groovyShell = new GroovyShell();
        Script script = groovyShell.parse(code);
        Object build = InvokerHelper.invokeMethod(script, "generate", new Object[]{env, selectedApp == null ? null : selectedApp.getPort(), httpMethod, path, params, jsonBody});
        return build == null ? "" : String.valueOf(build);
    }


    protected JPanel createActionPanel() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        actionComboBox = addActionComboBox(CallMethodIconProvider.CALL_METHOD_ICON, CALL_METHOD_DISABLE_ICON,
                "<strong>call-method</strong>\n<ul>\n" +
                        "    <li>spring bean 的 public 函数</li>\n" +
                        "    <li>非init/main</li>\n" +
                        "    <li>非test source</li>\n" +
                        "</ul>", topPanel, new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        triggerChangeAction();
                    }
                });
        // Populate methodComboBox with method names


        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(1, 3, 3, 3);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 2;
        gbc.gridy = 0;

        // Add the radio button
        useProxyButton = new JToggleButton(PROXY_ICON, true);
        useProxyButton.setPreferredSize(new Dimension(32, 32));
        useProxyButton.setToolTipText("use proxy obj call method");
        useProxyButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (useProxyButton.isSelected()) {
                    useProxyButton.setIcon(PROXY_ICON);
                    useProxyButton.setToolTipText("use proxy obj call method");
                } else {
                    useProxyButton.setIcon(PROXY_DISABLE_ICON);
                    useProxyButton.setToolTipText("use original obj call method");
                }
            }
        });
        topPanel.add(useProxyButton, gbc);

        JButton runButton = new JButton(AllIcons.Actions.Execute);
        runButton.setToolTipText("test method");
        //        // 设置按钮大小
        Dimension buttonSize = new Dimension(32, 32);
        runButton.setPreferredSize(buttonSize);

        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FlingToolWindow.VisibleApp app = getSelectedApp();
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
                            setOutputText("pls select method");
                            return null;
                        }
                        selectedItem.setArgs(jsonInput);
                        JSONObject params = buildParams(selectedItem.getMethod(), jsonObject, PluginToolEnum.CALL_METHOD.getCode());
                        try {
                            return HttpUtil.sendPost("http://localhost:" + app.getSidePort() + "/", params, JSONObject.class);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                });
            }
        });

        gbc.gridx = 3;
        topPanel.add(runButton, gbc);

        return topPanel;
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

        LocalStorageHelper.MonitorConfig monitorConfig = LocalStorageHelper.getMonitorConfig(getProject());
        req.put("monitor", monitorConfig.isEnable());
        req.put("monitorPrivate", monitorConfig.isMonitorPrivate());
        if (useScript) {
            req.put("script", LocalStorageHelper.getAppScript(getProject(), getSelectedAppName()));
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
        boolean requestMethod = isRequestMethod(method);
        if (requestMethod && !actionGroup.containsAction(copyCurlAction)) {
            actionGroup.add(copyCurlAction);
        } else if (!requestMethod && actionGroup.containsAction(copyCurlAction)) {
            actionGroup.remove(copyCurlAction);
        }
        ToolHelper.initParamsTextField(inputEditorTextField, methodAction);
    }


    private boolean isRequestMethod(PsiMethod method) {
        if (method == null) {
            return false;
        }

        // Check if the method has any of the request mapping annotations
        String[] requestMethodAnnotations = new String[]{
                "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.GetMapping",
                "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PutMapping",
                "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PatchMapping"
        };

        for (String annotationFqn : requestMethodAnnotations) {
            if (method.getModifierList().findAnnotation(annotationFqn) != null) {
                return true;
            }
        }

        // Check if the class has @RestController or @Controller annotation
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
            PsiAnnotation restControllerAnnotation = containingClass.getModifierList().findAnnotation("org.springframework.web.bind.annotation.RestController");
            PsiAnnotation controllerAnnotation = containingClass.getModifierList().findAnnotation("org.springframework.stereotype.Controller");

            if (restControllerAnnotation != null || controllerAnnotation != null) {
                return true;
            }
        }

        return false;
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
