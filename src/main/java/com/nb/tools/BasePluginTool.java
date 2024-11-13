package com.nb.tools;

import com.alibaba.fastjson.JSONObject;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.nb.util.HttpUtil;
import com.nb.view.WindowHelper;
import com.nb.view.PluginToolWindow;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Supplier;

public abstract class BasePluginTool {

    protected Set<String> cancelReqs = new HashSet<>(128);
    protected String lastReqId;

    protected PluginToolWindow toolWindow;
    protected PluginToolEnum tool;
    protected JPanel panel;  // 为减少内存占用，建议在构造中初始化
    protected EditorTextField inputEditorTextField;
    protected EditorTextField outputTextArea;

    public BasePluginTool(PluginToolWindow pluginToolWindow) {
        // 初始化panel
        this.toolWindow = pluginToolWindow;
        this.panel = new JPanel(new GridBagLayout());
        initializePanel();
    }


    public PluginToolEnum getTool() {
        return tool;
    }

    public JPanel getPanel() {
        return panel;
    }

    public Project getProject() {
        return toolWindow.getProject();
    }

    public WindowHelper.VisibleApp getSelectedApp() {
        return toolWindow.getSelectedApp();
    }

    public String getSelectedAppName() {
        return toolWindow.getSelectedAppName();
    }

    public static String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        while (throwable instanceof InvocationTargetException) {
            throwable = ((InvocationTargetException) throwable).getTargetException();
        }
        if (throwable.getCause() != null && throwable.getCause() != throwable) {
            throwable = throwable.getCause();
        }
        final StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw, true));
        return sw.toString();
    }

    protected static String buildMethodKey(PsiMethod method) {
        if (method == null) {
            return null;
        }
        // 获取类名
        PsiClass containingClass = method.getContainingClass();
        String className = containingClass != null ? containingClass.getName() : "UnknownClass";

        // 获取方法名
        String methodName = method.getName();

        // 获取参数列表
        StringBuilder parameters = new StringBuilder();
        PsiParameterList parameterList = method.getParameterList();
        for (PsiParameter parameter : parameterList.getParameters()) {
            if (parameters.length() > 0) {
                parameters.append(", ");
            }
            parameters.append(parameter.getType().getCanonicalText());
        }

        // 构建完整的方法签名
        return className + "#" + methodName + "(" + parameters.toString() + ")";
    }

    protected static String buildXmlTagKey(XmlTag xmlTag) {
        if (xmlTag == null) {
            return null;
        }

        // 获取标签名
        String tagName = xmlTag.getContainingFile().getName();

        // 构建标识符，假设我们使用标签名称和某个关键属性进行组合
        StringBuilder keyBuilder = new StringBuilder(tagName);

        // 假设我们关注某个特定属性，比如 "id"，这个可以根据具体业务规则定制
        String idAttribute = xmlTag.getAttributeValue("id");
        if (idAttribute != null) {
            keyBuilder.append("#").append(idAttribute);
        }

        // 当然，你可以根据需要加入更多的信息，比如标签的命名空间或其他属性
        // String namespace = xmlTag.getNamespace();
        // keyBuilder.append("(namespace: ").append(namespace).append(")");

        return keyBuilder.toString();
    }

    protected String getBeanNameFromClass(PsiClass psiClass) {
        PsiModifierList modifierList = psiClass.getModifierList();
        String beanName = null;

        if (modifierList != null) {
            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                String qualifiedName = annotation.getQualifiedName();
                if (qualifiedName != null &&
                        (qualifiedName.equals("org.springframework.stereotype.Component") ||
                                qualifiedName.equals("org.springframework.stereotype.Service") ||
                                qualifiedName.equals("org.springframework.stereotype.Repository") ||
                                qualifiedName.equals("org.springframework.context.annotation.Configuration") ||
                                qualifiedName.equals("org.springframework.stereotype.Controller") ||
                                qualifiedName.equals("org.springframework.web.bind.annotation.RestController") ||
                                qualifiedName.equals("org.apache.ibatis.annotations.Mapper"))) {

                    if (annotation.findAttributeValue("value") != null) {
                        beanName = annotation.findAttributeValue("value").getText().replace("\"", "");
                    }
                }
            }
        }

        if (beanName == null || beanName.isBlank()) {
            String className = psiClass.getName();
            if (className != null && !className.isEmpty()) {
                beanName = Character.toLowerCase(className.charAt(0)) + className.substring(1);
            }
        }

        return beanName;
    }

    protected void initParams(EditorTextField inputEditorTextField, PsiMethod method) {
        // 在这里执行耗时操作
        ArrayList<Object> initParams = doInitParams(method);
        inputEditorTextField.setText(JSONObject.toJSONString(initParams, true));
    }

    protected ArrayList<Object> doInitParams(PsiMethod method) {
        ArrayList<Object> initParams = new ArrayList<>();
        PsiParameter[] parameters = method.getParameterList().getParameters();

        for (PsiParameter parameter : parameters) {
            PsiType type = parameter.getType();

            if (type.equalsToText("boolean") || type.equalsToText("java.lang.Boolean")) {
                initParams.add(false);
            } else if (type.equalsToText("char") || type.equalsToText("java.lang.Character")) {
                initParams.add('\0');
            } else if (type.equalsToText("byte") || type.equalsToText("java.lang.Byte")) {
                initParams.add((byte) 0);
            } else if (type.equalsToText("short") || type.equalsToText("java.lang.Short")) {
                initParams.add((short) 0);
            } else if (type.equalsToText("int") || type.equalsToText("java.lang.Integer")) {
                initParams.add(0);
            } else if (type.equalsToText("long") || type.equalsToText("java.lang.Long")) {
                initParams.add(0L);
            } else if (type.equalsToText("float") || type.equalsToText("java.lang.Float")) {
                initParams.add(0.0f);
            } else if (type.equalsToText("double") || type.equalsToText("java.lang.Double")) {
                initParams.add(0.0);
            } else if (type.equalsToText("java.lang.String")) {
                initParams.add("");
            } else if (type.equalsToText("java.util.Date")) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                initParams.add(sdf.format(new Date()));
            } else if (isEnumType(type)) {
                initParams.add(getFirstEnumConstant(type));
            } else if (isCollectionType(type)) {
                initParams.add(initializeCollection(type));
            } else if (isMapType(type)) {
                initParams.add(initializeMap(type));
            } else if (type instanceof PsiArrayType) {
                initParams.add(initializeArray(type));
            } else {
                // 为对象类型初始化一个带有可设置字段的 HashMap
                initParams.add(initializeObjectFields(type));
            }
        }

        return initParams;
    }

    private boolean isEnumType(PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType) type).resolve();
            return psiClass != null && psiClass.isEnum();
        }
        return false;
    }

    private Object getFirstEnumConstant(PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType) type).resolve();
            if (psiClass != null && psiClass.isEnum()) {
                PsiField[] fields = psiClass.getFields();
                for (PsiField field : fields) {
                    if (field instanceof PsiEnumConstant) {
                        return field.getName(); // 返回第一个枚举常量名称
                    }
                }
            }
        }
        return null; // 如果没有枚举常量，可返回默认值或 null
    }

    private boolean isCollectionType(PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            return InheritanceUtil.isInheritor(classType, "java.util.Collection");
        }
        return false;
    }

    private boolean isMapType(PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            return InheritanceUtil.isInheritor(classType, "java.util.Map");
        }
        return false;
    }

    private Object initializeCollection(PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            PsiType[] parameters = classType.getParameters();

            // 默认空集合
            ArrayList<Object> collectionInit = new ArrayList<>();

            if (parameters.length == 1) {
                PsiType elementType = parameters[0];
                if (elementType.equalsToText("java.lang.String")) {
                    collectionInit.add("");
                } else if (elementType.equalsToText("java.lang.Integer")) {
                    collectionInit.add(0);
                } else if (elementType.equalsToText("java.lang.Boolean")) {
                    collectionInit.add(false);
                } else if (elementType.equalsToText("java.util.Date")) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    collectionInit.add(sdf.format(new Date()));
                } else if (isEnumType(elementType)) {
                    collectionInit.add(getFirstEnumConstant(elementType));
                } else if (elementType instanceof PsiClassType) {
                    // 对于对象类型的元素，初始化一个空对象结构
                    collectionInit.add(initializeObjectFields(elementType));
                }
            }

            return collectionInit;
        }

        // 没有泛型参数时返回空列表
        return new ArrayList<>();
    }

    private Object initializeMap(PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            PsiType[] parameters = classType.getParameters();

            HashMap<Object, Object> mapInit = new HashMap<>();

            if (parameters.length == 2) { // Map should have two type parameters: key and value
                PsiType keyType = parameters[0];
                PsiType valueType = parameters[1];

                Object defaultKey = getDefaultValueForType(keyType);
                Object defaultValue = getDefaultValueForType(valueType);

                mapInit.put(defaultKey, defaultValue);
            }

            return mapInit;
        }

        return new HashMap<>();
    }

    private Object initializeArray(PsiType type) {
        if (type instanceof PsiArrayType) {
            PsiArrayType arrayType = (PsiArrayType) type;
            PsiType componentType = arrayType.getComponentType();

            ArrayList<Object> arrayInit = new ArrayList<>();

            Object defaultValue = getDefaultValueForType(componentType);
            arrayInit.add(defaultValue);

            return arrayInit;
        }

        return new ArrayList<>();
    }

    private Object getDefaultValueForType(PsiType type) {
        if (type.equalsToText("java.lang.String")) {
            return "";
        } else if (type.equalsToText("java.lang.Integer") || type.equalsToText("int")) {
            return 0;
        } else if (type.equalsToText("java.lang.Boolean") || type.equalsToText("boolean")) {
            return false;
        } else if (type.equalsToText("java.util.Date")) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(new Date());
        } else if (isEnumType(type)) {
            return getFirstEnumConstant(type);
        } else if (isCollectionType(type)) {
            return initializeCollection(type);
        } else if (isMapType(type)) {
            return initializeMap(type);
        } else if (type instanceof PsiArrayType) {
            return initializeArray(type);
        } else {
            return initializeObjectFields(type);
        }
    }

    private Object initializeObjectFields(PsiType type) {
        if (!(type instanceof PsiClassType)) {
            return null;
        }

        PsiClassType classType = (PsiClassType) type;
        PsiClass psiClass = classType.resolve();

        if (psiClass == null) {
            return new HashMap<>();
        }

        HashMap<String, Object> fieldValues = new HashMap<>();

        for (PsiMethod method : psiClass.getAllMethods()) {
            if (method.getName().startsWith("set") && method.getParameterList().getParametersCount() == 1) {
                PsiParameter setterParameter = method.getParameterList().getParameters()[0];
                PsiType fieldType = setterParameter.getType();

                // 初始化字段值
                if (fieldType.equalsToText("java.lang.String")) {
                    fieldValues.put(method.getName().substring(3), "");
                } else if (fieldType.equalsToText("java.lang.Integer") || fieldType.equalsToText("int")) {
                    fieldValues.put(method.getName().substring(3), 0);
                } else if (fieldType.equalsToText("java.lang.Boolean") || fieldType.equalsToText("boolean")) {
                    fieldValues.put(method.getName().substring(3), false);
                } else if (fieldType.equalsToText("java.util.Date")) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    fieldValues.put(method.getName().substring(3), sdf.format(new Date()));
                } else if (isEnumType(fieldType)) {
                    fieldValues.put(method.getName().substring(3), getFirstEnumConstant(fieldType));
                } else if (isCollectionType(fieldType)) {
                    fieldValues.put(method.getName().substring(3), initializeCollection(fieldType));
                } else if (isMapType(fieldType)) {
                    fieldValues.put(method.getName().substring(3), new HashMap<>());
                } else {
                    // 如果是对象类型，递归调用初始化
                    fieldValues.put(method.getName().substring(3), initializeObjectFields(fieldType));
                }
            }
        }

        return fieldValues;
    }

    protected void initializePanel() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0; // 顶部面板不占用垂直空间

        // Top panel for method selection and actions
        JPanel topPanel = createTopPanel();
        panel.add(topPanel, gbc);

        gbc.gridy = 1;
        gbc.weighty = 0.3; // Middle panel takes 30% of the space

        // Middle panel for input parameters
        JPanel middlePanel = createMiddlePanel();
        panel.add(middlePanel, gbc);

        gbc.gridy = 2;
        gbc.weighty = 0.6; // Bottom panel takes 60% of the space

        // Bottom panel for output
        JPanel bottomPanel = createBottomPanel();
        panel.add(bottomPanel, gbc);
    }

    protected abstract JPanel createTopPanel();

    protected JPanel createMiddlePanel() {
        JPanel middlePanel = new JPanel(new BorderLayout());
        inputEditorTextField = new LanguageTextField(JsonLanguage.INSTANCE, getProject(), "", false);
        middlePanel.add(new JBScrollPane(inputEditorTextField), BorderLayout.CENTER);

        return middlePanel;
    }

    protected JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        outputTextArea = new LanguageTextField(JsonLanguage.INSTANCE, getProject(), "", false);
        bottomPanel.add(new JBScrollPane(outputTextArea), BorderLayout.CENTER);
        return bottomPanel;
    }

    protected void triggerTask(JButton triggerBtn, Icon executeIcon, EditorTextField outputTextArea, int sidePort, Supplier<JSONObject> submit) {
        if (AllIcons.Actions.Suspend.equals(triggerBtn.getIcon())) {
            if (lastReqId == null) {
                triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
                return;
            }
            cancelReqs.add(lastReqId);
            new SwingWorker<JSONObject, Void>(){
                @Override
                protected JSONObject doInBackground() throws Exception {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("method", "stop_task");
                    HashMap<Object, Object> params = new HashMap<>();
                    params.put("reqId", lastReqId);
                    map.put("params", params);
                    return HttpUtil.sendPost("http://localhost:" + sidePort + "/", map, JSONObject.class);
                }

                @Override
                protected void done() {
                    triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
                    try {
                        JSONObject result = get();
                        if (cancelReqs.remove(lastReqId)) {
                            outputTextArea.setText("req is cancel, reqId:" + lastReqId);
                        }
                    } catch (Throwable e) {
                        if (cancelReqs.remove(lastReqId)) {
                            outputTextArea.setText("cancel req error, reqId:" + lastReqId + "\n" + getStackTrace(e));
                        }
                    }
                }
            }.execute();
            return;
        }
        // 发起任务请求，获取请求ID
        JSONObject response = null;
        try {
            response = submit.get();
        } catch (Throwable e) {
            outputTextArea.setText("submit req error \n" + getStackTrace(e));
            return;
        }
        if (response == null) {
            return;
        }
        if (!response.getBooleanValue("success") || response.getString("data") == null) {
            outputTextArea.setText("submit req error \n" + response.getString("message"));
            return;
        }
        String reqId = response.getString("data");
        lastReqId = reqId;
        triggerBtn.setIcon(AllIcons.Actions.Suspend);
        outputTextArea.setText("req is sent，reqId:" + reqId);
        // 第二个 SwingWorker 用于定时轮询获取结果
        new SwingWorker<JSONObject, Void>() {

            @Override
            protected JSONObject doInBackground() throws Exception {
                HashMap<String, Object> map = new HashMap<>();
                map.put("method", "get_task_ret");
                HashMap<Object, Object> params = new HashMap<>();
                params.put("reqId", reqId);
                map.put("params", params);
                return HttpUtil.sendPost("http://localhost:" + sidePort + "/", map, JSONObject.class);
            }

            @Override
            protected void done() {
                try {
                    JSONObject result = get();
                    if (cancelReqs.remove(reqId)) {
                        System.out.println("请求已被取消，结果丢弃");
                    } else if (!result.getBooleanValue("success")) {
                        outputTextArea.setText("req is error\n" + result.getString("message"));
                    } else {
                        outputTextArea.setText(JSONObject.toJSONString(result.get("data"), true));
                    }
                } catch (Throwable ex) {
                    outputTextArea.setText("wait req is error\n" + getStackTrace(ex));
                } finally {
                    triggerBtn.setIcon(executeIcon == null ? AllIcons.Actions.Execute : executeIcon);
                }
            }
        }.execute();
    }

    protected ComboBox buildActionComboBox() {
        ComboBox actionComboBox = new ComboBox<>();
        actionComboBox.setPreferredSize(new Dimension(200, 32));
        actionComboBox.addItemListener(e -> {
            Object selectedItem = actionComboBox.getSelectedItem();
            actionComboBox.setToolTipText(selectedItem==null?"":selectedItem.toString()); // 动态更新 ToolTipText
        });
        return actionComboBox;
    }

    public static class MethodAction {

        private final PsiMethod method;

        public MethodAction(PsiMethod method) {
            this.method = method;
        }

        @Override
        public String toString() {
            if (method == null) {
                return "unknown";
            }
            return buildMethodKey(method);
        }

        public PsiMethod getMethod() {
            return method;
        }
    }

    public static class XmlTagAction {

        private final XmlTag xmlTag;

        public XmlTagAction(XmlTag xmlTag) {
            this.xmlTag = xmlTag;
        }

        @Override
        public String toString() {
            if (xmlTag == null) {
                return "unknown";
            }
            return buildXmlTagKey(xmlTag);
        }

        public XmlTag getXmlTag() {
            return xmlTag;
        }
    }

}