package com.halo.plugin.tools;

import com.alibaba.fastjson.JSONObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.halo.plugin.view.PluginToolWindow;
import com.halo.plugin.view.VisibleApp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;

public class BasePluginTool {


    protected PluginToolWindow toolWindow;
    protected PluginToolEnum tool;
    protected JPanel panel;  // 为减少内存占用，建议在构造中初始化
    protected JComboBox<String> actionBox;
    protected JButton runButton;
    protected JTextArea resultArea;

    public BasePluginTool(PluginToolWindow pluginToolWindow) {
        // 初始化panel
        this.toolWindow = pluginToolWindow;
        this.panel = new JPanel(new GridBagLayout());
    }

    protected void addActionBox() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        actionBox = new ComboBox<>(new String[]{tool + " Action 1", "Action 2", "Action 3"});
        panel.add(actionBox, gbc);
    }

    protected void addRunButton() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;

        runButton = new JButton("Run");
        panel.add(runButton, gbc);
    }

    protected void addResultArea() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(resultArea);
        panel.add(scrollPane, gbc);
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

    public VisibleApp getVisibleApp() {
        return toolWindow.getSelectedApp();
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