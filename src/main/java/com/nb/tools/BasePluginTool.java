package com.nb.tools;

import com.intellij.json.JsonLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.nb.view.PluginToolWindow;
import com.nb.view.VisibleApp;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;

public abstract class BasePluginTool {


    protected PluginToolWindow toolWindow;
    protected PluginToolEnum tool;
    protected JPanel panel;  // 为减少内存占用，建议在构造中初始化
    protected LanguageTextField inputEditorTextField;
    protected LanguageTextField outputTextArea;

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

    public VisibleApp getSelectedApp() {
        return toolWindow.getSelectedApp();
    }

    public String getSelectedAppName() {
        return toolWindow.getSelectedAppName();
    }

    public static String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        final StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw, true));
        return sw.toString();
    }

    protected static String buildMethodKey(PsiMethod method) {
        if (method==null) {
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
                                qualifiedName.equals("org.springframework.web.bind.annotation.RestController")||
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

    protected ArrayList initParams(PsiMethod method){
        ArrayList initParams = new ArrayList();
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
            } else if (type.equalsToText("java.util.Collection") || type.equalsToText("java.util.List")|| type.equalsToText("java.util.ArrayList") || type.equalsToText("java.util.HashSet") || type.equalsToText("java.util.Set")) {
                initParams.add(new ArrayList<>());
            } else if (type.equalsToText("java.util.Map")) {
                initParams.add(new HashMap<>());
            } else if (type instanceof PsiArrayType) {
                initParams.add(new ArrayList<>()); // 可以使用空集合来表示数组
            } else {
                initParams.add(new HashMap<>()); // 为复杂对象类型初始化空映射
            }
        }
        return initParams;
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

    public static class MethodAction {

        private final PsiMethod method;

        public MethodAction(PsiMethod method) {
            this.method = method;
        }

        @Override
        public String toString() {
            if (method==null) {
                return "unknown";
            }
            return buildMethodKey(method);
        }

        public PsiMethod getMethod() {
            return method;
        }
    }

}