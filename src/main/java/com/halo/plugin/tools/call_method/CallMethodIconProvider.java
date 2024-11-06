package com.halo.plugin.tools.call_method;

import com.halo.plugin.tools.PluginToolEnum;
import com.halo.plugin.view.WindowHelper;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class CallMethodIconProvider implements LineMarkerProvider {

    private static final Icon CALL_METHOD_ICON = IconLoader.getIcon("/icons/run.svg", CallMethodIconProvider.class);


    @Nullable
    @Override
    public LineMarkerInfo<PsiElement> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PsiMethod)) {
            return null;
        }

        PsiMethod method = (PsiMethod) element;
        if (!isBeanMethod(method.getModifierList())) {
            return null;
        }
        return new LineMarkerInfo<>(
                method,
                method.getTextRange(),
                CALL_METHOD_ICON,
                new Function<PsiElement, String>() {
                    @Override
                    public String fun(PsiElement element) {
                        return "call this method";
                    }
                },
                new GutterIconNavigationHandler() {
                    @Override
                    public void navigate(MouseEvent e, PsiElement elt) {
                        if (GraphicsEnvironment.isHeadless()) {
                            throw new HeadlessException("Cannot display UI elements in a headless environment.");
                        }
                        Project project = elt.getProject();
                        WindowHelper.switch2Tool(project, PluginToolEnum.CALL_METHOD, elt);
                    }
                },
                GutterIconRenderer.Alignment.RIGHT
        );
    }

    private boolean isBeanMethod(@NotNull PsiModifierList modifierList) {

        // 1. 检查方法是否为 public
        if (!modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        // 2. 检查方法是否为非静态
        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }

        // 3. 获取方法和类
        PsiElement parent = modifierList.getParent();
        if (!(parent instanceof PsiMethod)) {
            return false;
        }
        PsiMethod psiMethod = (PsiMethod) parent;

        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        // 4. 检查类是否含有 Spring 可注册 Bean 的注解
        if (!isSpringBean(containingClass)) {
            return false;
        }

        PsiFile psiFile = psiMethod.getContainingFile();
        if (!(psiFile instanceof PsiJavaFile)) {
            return false;
        }

        // 3. 必须不在Java的test模块下
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            return false;
        }
        // 获取文件的完整路径
        String filePath = virtualFile.getPath();
        // 提取类名和包名部分
        String packageAndClassPath = filePath.substring(0, filePath.lastIndexOf('/'));
        String packagePath = packageAndClassPath.substring(0, packageAndClassPath.length() - ((PsiJavaFile) psiFile).getPackageName().replace(".", "/").length());
        // 检查路径是否以 src/test/java 结尾
        if (packagePath.endsWith("src/test/java/")) {
            return false;
        }

        return true;
    }

    private boolean isSpringBean(@NotNull PsiClass psiClass) {
        // 列举常见的 Spring 注解
        String[] springAnnotations = {
                "org.springframework.stereotype.Component",
                "org.springframework.stereotype.Service",
                "org.springframework.stereotype.Repository",
                "org.springframework.stereotype.Controller",
                "org.apache.ibatis.annotations.Mapper",
                "org.springframework.web.bind.annotation.RestController",
                "org.springframework.context.annotation.Configuration",
        };

        for (String annotationName : springAnnotations) {
            if (psiClass.hasAnnotation(annotationName)) {
                return true;
            }
        }

        return false;
    }
}