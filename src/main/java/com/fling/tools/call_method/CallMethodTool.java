package com.fling.tools.call_method;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fling.FlingHelper;
import com.fling.tools.ToolHelper;
import com.fling.util.Container;
import com.fling.view.FlingToolWindow;
import com.google.gson.Gson;
import com.intellij.icons.AllIcons;
import com.fling.util.HttpUtil;
import com.fling.LocalStorageHelper;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.fling.tools.BasePluginTool;
import com.fling.tools.PluginToolEnum;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.jcef.JBCefBrowser;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CallMethodTool extends BasePluginTool {

    public static final Icon CALL_METHOD_DISABLE_ICON = IconLoader.getIcon("/icons/spring-fling-disable.svg", CallMethodIconProvider.class);

    public static final Icon PROXY_DISABLE_ICON = IconLoader.getIcon("/icons/proxy-disable.svg", CallMethodIconProvider.class);
    public static final Icon PROXY_ICON = IconLoader.getIcon("/icons/proxy.svg", CallMethodIconProvider.class);
    public static final Icon COPY_CURL_ICON = IconLoader.getIcon("/icons/copy-curl.svg", CallMethodIconProvider.class);


    private JComboBox<ToolHelper.MethodAction> actionComboBox;

    private JToggleButton useProxyButton;

    protected AnAction copyCurlAction;

    {
        this.tool = PluginToolEnum.CALL_METHOD;
    }

    public CallMethodTool(FlingToolWindow flingToolWindow) {
        super(flingToolWindow);
        copyCurlAction = new AnAction("Copy curl to clipboard", "Copy curl to clipboard", COPY_CURL_ICON) {

            @Override
            public void actionPerformed(AnActionEvent e) {
                ToolHelper.MethodAction selectedItem = (ToolHelper.MethodAction) actionComboBox.getSelectedItem();
                if (selectedItem == null) {
                    return;
                }

                PsiMethod method = selectedItem.getMethod();
                String jsonParams = inputEditorTextField.getText();
                String httpMethod = "GET"; // Default to GET
                String endpoint = "";

                PsiClass containingClass = method.getContainingClass();
                if (containingClass.hasAnnotation("org.springframework.web.bind.annotation.RequestMapping")) {
                    PsiAnnotation annotation = containingClass.getAnnotation("org.springframework.web.bind.annotation.RequestMapping");
                    PsiAnnotationMemberValue classValue = annotation.findAttributeValue("value");
                    if (classValue == null || (classValue instanceof PsiLiteralExpression && StringUtils.isBlank(String.valueOf(((PsiLiteralExpression) classValue).getValue())))) {
                        classValue = annotation.findAttributeValue("path");
                    }
                    if (classValue instanceof PsiLiteralExpression && StringUtils.isNotBlank((CharSequence) ((PsiLiteralExpression) classValue).getValue())) {
                        String s = String.valueOf(((PsiLiteralExpression) classValue).getValue());
                        if (s.startsWith("/")) {
                            endpoint += s;
                        } else {
                            endpoint += ("/" + s);
                        }
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
                        PsiAnnotationMemberValue methodValue = methodAnnotation.findAttributeValue("method");
                        if (methodValue instanceof PsiArrayInitializerMemberValue) {
                            PsiArrayInitializerMemberValue arrayValue = (PsiArrayInitializerMemberValue) methodValue;
                            if (arrayValue.getInitializers().length > 0) {
                                PsiAnnotationMemberValue firstMethodValue = arrayValue.getInitializers()[0];
                                if (firstMethodValue instanceof PsiReferenceExpression) {
                                    PsiReferenceExpression reference = (PsiReferenceExpression) firstMethodValue;
                                    String methodName = reference.getReferenceName();
                                    if (methodName != null) {
                                        httpMethod = methodName; // 假设这里返回的是类似 "GET", "POST" 等字符串
                                    }
                                }
                            }
                        }
                    }

                    PsiAnnotationMemberValue methodValue = methodAnnotation.findAttributeValue("value");
                    if (methodValue == null || (methodValue instanceof PsiLiteralExpression && StringUtils.isBlank(String.valueOf(((PsiLiteralExpression) methodValue).getValue())))) {
                        methodValue = methodAnnotation.findAttributeValue("path");
                    }

                    if (methodValue instanceof PsiLiteralExpression && StringUtils.isNotBlank(String.valueOf(((PsiLiteralExpression) methodValue).getValue()))) {
                        String s = String.valueOf(((PsiLiteralExpression) methodValue).getValue());
                        if (endpoint.endsWith("/")) {
                            if (s.startsWith("/")) {
                                endpoint += s.substring(1);
                            } else {
                                endpoint += methodValue.getText();
                            }
                        } else {
                            if (s.startsWith("/")) {
                                endpoint += s;
                            } else {
                                endpoint += ("/" + s);
                            }
                        }
                    }
                    break;
                }

                StringBuilder header = new StringBuilder();

                // Add authorization header
                header.append("  -H 'Authorization: Bearer your_token'");

                // Process parameters
                // Get the parameters
                PsiParameter[] parameters = method.getParameterList().getParameters();
                JSONObject parse = JSONObject.parseObject(jsonParams);
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

                if (requestBodyKey != null) {
                    String requestBody = null;
                    if (parse.get(requestBodyKey) != null) {
                        requestBody = JSONObject.toJSONString(parse.get(requestBodyKey), SerializerFeature.WriteMapNullValue);
                    } else {
                        requestBody = "{}";
                    }
                    parse.remove(requestBodyKey);
                    header.append(" \\\n  -H 'Content-Type: application/json' \\\n  -d '").append(requestBody).append("'");
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
                            val = URLEncoder.encode(String.valueOf(flattenedParse.get(path)),"UTF-8");
                        } catch (UnsupportedEncodingException ex) {
                        }
                        flattenedParse.remove(path);

                        if (aliasmap.containsKey(path)) {
                            path = aliasmap.get(path);
                        }
                        endpoint = endpoint.replace("{" + path + "}", val);
//                        path 支持写类似正则的内容,类似/{userId1}/orders/{orderId:\\d+}
//                        请手动在 endpoint 中找{+path:的内容再找到下一个}进行补充替换
                        // 处理带有正则的路径变量，例如：/{userId1}/orders/{orderId:\\d+}
                        Pattern pattern = Pattern.compile("\\{" + Pattern.quote(path) + ":.*?\\}");
                        Matcher matcher = pattern.matcher(endpoint);
                        if (matcher.find()) {
                            endpoint = matcher.replaceFirst(val);
                        }
                    }
                }

                FlingToolWindow.VisibleApp selectedApp = getSelectedApp();
                StringBuilder urlBuilder = new StringBuilder();
                urlBuilder.append("curl -X ").append(httpMethod.toUpperCase()).append(" 'http://localhost:" + (selectedApp == null ? "8080" : selectedApp.getPort())).append(endpoint);
                if (!flattenedParse.isEmpty()) {
                    Container<Boolean> first = new Container<>();
                    first.set(true);
                    flattenedParse.entrySet().forEach(new Consumer<Map.Entry<String, Object>>() {
                        @Override
                        public void accept(Map.Entry<String, Object> stringObjectEntry) {
                            try {
                                String s = aliasmap.get(stringObjectEntry.getKey());
                                if (s != null && StringUtils.isBlank(s)) {
                                    return;
                                }
                                s = s == null ? stringObjectEntry.getKey() : s;
                                if (!first.get()) {
                                    urlBuilder.append("?");
                                    first.set(false);
                                } else {
                                    urlBuilder.append("&");
                                }
                                urlBuilder.append(s).append("=").append(URLEncoder.encode(String.valueOf(stringObjectEntry.getValue()), "UTF-8"));
                            } catch (UnsupportedEncodingException ex) {
                            }
                        }
                    });
                }
                urlBuilder.append("'");

                FlingHelper.copyToClipboard(getProject(), urlBuilder + " \\\n" + header, "The curl was copied to the clipboard, Please fill in the Authorization header yourself.");
            }

        };
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
