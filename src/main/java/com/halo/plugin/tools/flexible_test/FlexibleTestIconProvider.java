package com.halo.plugin.tools.flexible_test;

import com.halo.plugin.tools.PluginToolEnum;
import com.halo.plugin.view.WindowHelper;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class FlexibleTestIconProvider implements LineMarkerProvider {

    private static final Icon FLEXIBLE_TEST_ICON = IconLoader.getIcon("/icons/run.svg", FlexibleTestIconProvider.class);


    @Nullable
    @Override
    public LineMarkerInfo<PsiElement> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PsiMethod)) {
            return null;
        }

        PsiMethod method = (PsiMethod) element;
        if (!isTest(method.getModifierList())) {
            return null;
        }
        return new LineMarkerInfo<>(
                method,
                method.getTextRange(),
                FLEXIBLE_TEST_ICON,
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
                        WindowHelper.switch2Tool(project, PluginToolEnum.FLEXIBLE_TEST, elt);
                    }
                },
                GutterIconRenderer.Alignment.RIGHT
        );
    }

    private boolean isTest(PsiModifierList modifierList) {

        // 1. 必须是个public的函数
        if (modifierList == null || !modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        // 2. 必须是非静态方法
        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }

        // 获取方法所在的文件
        PsiElement parent = modifierList.getParent();
        if (!(parent instanceof PsiMethod)) {
            return false;
        }
        PsiMethod method = (PsiMethod) parent;
        PsiFile psiFile = method.getContainingFile();
        if (!(psiFile instanceof PsiJavaFile)) {
            return false;
        }

        // 3. 必须是在Java的test模块下
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            return false;
        }
        // 获取文件的完整路径
        String filePath = virtualFile.getPath();
        String packageName = ((PsiJavaFile) psiFile).getPackageName();
        // 提取类名和包名部分
        String packageAndClassPath = filePath.substring(0, filePath.lastIndexOf('/'));
        String packagePath = packageAndClassPath.substring(0, packageAndClassPath.length() - ((PsiJavaFile) psiFile).getPackageName().replace(".", "/").length());
        // 检查路径是否以 src/test/java 结尾
        if (!packagePath.endsWith("src/test/java/")) {
            return false;
        }

        String flexibleTestPackage = WindowHelper.getFlexibleTestPackage(modifierList.getProject());
        if (flexibleTestPackage == null) {
            return false;
        }
        // 4. 必须在指定的包下
        return packageName.contains(flexibleTestPackage);
    }
}