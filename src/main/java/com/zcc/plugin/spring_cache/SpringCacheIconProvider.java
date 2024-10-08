package com.zcc.plugin.spring_cache;

import com.alibaba.fastjson.JSONObject;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiClass;
import com.zcc.plugin.HttpUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpringCacheIconProvider implements LineMarkerProvider, Disposable {

    private static final Icon CACHEABLE_ICON = new ImageIcon(SpringCacheIconProvider.class.getResource("/icons/cacheable.png"));
    private final Set<PsiElement> markedElements = new HashSet<>();
    private JFrame frame = null;

    @Nullable
    @Override
    public LineMarkerInfo<PsiElement> getLineMarkerInfo(@NotNull PsiElement element) {
        if (markedElements.contains(element)) {
            return null;
        }

        if (!(element instanceof PsiMethod)) {
            return null;
        }

        PsiMethod method = (PsiMethod) element;
        PsiModifierList modifierList = method.getModifierList();

        if (modifierList == null ||
                (!modifierList.hasAnnotation("org.springframework.cache.annotation.Cacheable") &&
                        !modifierList.hasAnnotation("org.springframework.cache.annotation.CacheEvict") &&
                        !modifierList.hasAnnotation("org.springframework.cache.annotation.CachePut"))) {
            return null;
        }

        markedElements.add(element);
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                CACHEABLE_ICON,
                __ -> "This method is cacheable",
                new CacheableIconNavigationHandler(method),
                GutterIconRenderer.Alignment.CENTER,
                () -> "Cacheable method"
        );
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
        for (PsiElement element : elements) {
            LineMarkerInfo<PsiElement> info = getLineMarkerInfo(element);
            if (info != null) {
                result.add(info);
            }
        }
    }

    // Get project from PsiElement
    private Project getProject(@NotNull PsiElement element) {
        return element.getProject();
    }

    @Override
    public void dispose() {
        if (frame != null) {
            frame.dispose();
        }
    }

    private class CacheableIconNavigationHandler implements GutterIconNavigationHandler<PsiElement> {
        private final PsiMethod method;

        CacheableIconNavigationHandler(PsiMethod method) {
            this.method = method;
        }

        @Override
        public void navigate(MouseEvent e, PsiElement elt) {
            if (GraphicsEnvironment.isHeadless()) {
                throw new HeadlessException("Cannot display UI elements in a headless environment.");
            }

            Project project = getProject(elt); // Get the project from PsiElement

            if (frame == null) {
                frame = new JFrame("Cacheable Method Input");
                Disposer.register(project, frame::dispose);
            }

            frame.getContentPane().removeAll();
            frame.setLayout(new BorderLayout());
            frame.setSize(800, 600);
            frame.setLocationRelativeTo(null);

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

            JPanel inputPanel = new JPanel(new BorderLayout());
            JLabel inputLabel = new JLabel("Input JSON:");
            JTextArea inputTextArea = new JTextArea();
            inputPanel.add(inputLabel, BorderLayout.NORTH);
            inputPanel.add(new JScrollPane(inputTextArea), BorderLayout.CENTER);

            JPanel outputPanel = new JPanel(new BorderLayout());
            JLabel outputLabel = new JLabel("Output:");
            JTextArea outputTextArea = new JTextArea();
            outputTextArea.setEditable(false);
            outputPanel.add(outputLabel, BorderLayout.NORTH);
            outputPanel.add(new JScrollPane(outputTextArea), BorderLayout.CENTER);

            splitPane.setLeftComponent(inputPanel);
            splitPane.setRightComponent(outputPanel);
            splitPane.setDividerLocation(400);

            frame.add(splitPane, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton submitButton = new JButton("Submit");
            buttonPanel.add(submitButton);
            frame.add(buttonPanel, BorderLayout.SOUTH);

            submitButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    String jsonInput = inputTextArea.getText();
                    if (jsonInput == null || jsonInput.isBlank()) {
                        outputTextArea.setText("参数框不可为空");
                        return;
                    }

                    try {
                        JSONObject paramsJson = buildParams(method, jsonInput);
                        System.out.println(paramsJson.toJSONString());
                        JSONObject response = HttpUtil.sendPost("http://localhost:9989/", paramsJson, JSONObject.class);
                        outputTextArea.setText(response.toJSONString());
                    } catch (Exception ex) {
                        outputTextArea.setText("Error: " + ex.getMessage());
                    }
                }
            });

            frame.setVisible(true);
        }

        private JSONObject buildParams(PsiMethod method, String args) throws Exception {
            JSONObject params = new JSONObject();
            PsiClass containingClass = method.getContainingClass();
            String typeClass = containingClass.getQualifiedName();
            params.put("typeClass", typeClass);

            String beanName = getBeanNameFromClass(containingClass);
            params.put("beanName", beanName);
            params.put("methodName", method.getName());

            PsiParameter[] parameters = method.getParameterList().getParameters();
            String[] argTypes = new String[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                argTypes[i] = parameters[i].getType().getCanonicalText();
            }
            params.put("argTypes", JSONObject.toJSONString(argTypes));
            params.put("args", args);
            JSONObject req = new JSONObject();
            req.put("method", "build_cache_key");
            req.put("params", params);
            return req;
        }

        private String getBeanNameFromClass(PsiClass psiClass) {
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
                                    qualifiedName.equals("org.springframework.web.bind.annotation.RestController"))) {

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
    }
}