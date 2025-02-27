package com.testkit.tools.flexible_test;

import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.tools.PluginToolEnum;
import com.testkit.tools.function_call.FunctionCallIconProvider;
import com.testkit.view.TestkitToolWindowFactory;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
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

public class FlexibleTestIconProvider implements LineMarkerProvider {

    public static final Icon FLEXIBLE_TEST_ICON = IconLoader.getIcon("/icons/test-code.svg", FunctionCallIconProvider.class);


    @Nullable
    @Override
    public LineMarkerInfo<PsiElement> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PsiMethod)) {
            return null;
        }

        PsiMethod method = (PsiMethod) element;
        String test = isTest(method.getModifierList());
        if (test != null) {
            System.out.println("not_support_test, " + test + ":" + method.getContainingClass() + "#" + method.getName());
            return null;
        }
        return new LineMarkerInfo<>(
                method,
                method.getTextRange(),
                FLEXIBLE_TEST_ICON,
                new Function<PsiElement, String>() {
                    @Override
                    public String fun(PsiElement element) {
                        return "Call this function";
                    }
                },
                new GutterIconNavigationHandler() {
                    @Override
                    public void navigate(MouseEvent e, PsiElement elt) {
                        if (GraphicsEnvironment.isHeadless()) {
                            throw new HeadlessException("Cannot display UI elements in a headless environment.");
                        }
                        Project project = elt.getProject();
                        TestkitToolWindowFactory.switch2Tool(project, PluginToolEnum.FLEXIBLE_TEST, elt);
                    }
                },
                GutterIconRenderer.Alignment.RIGHT
        );
    }

    private String isTest(PsiModifierList modifierList) {

        // 1. 必须是个public的函数
        if (modifierList == null || !modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            return "not_public";
        }

        // 2. 必须是非静态方法
        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            return "is_static";
        }

        // 获取方法所在的文件
        PsiElement parent = modifierList.getParent();
        if (!(parent instanceof PsiMethod)) {
            return "not_method";
        }

        PsiMethod method = (PsiMethod) parent;
        if (method.isConstructor()) {
            return "is_constructor";
        }

        // 新增内部类过滤逻辑
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || containingClass.getName() == null) {
            return "is_inner_class";
        }

        if (containingClass.isAnnotationType()) {
            return "annotationType";
        }

        PsiFile psiFile = method.getContainingFile();

        // 3. 必须是在Java的test模块下
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            return "not_find_virtualFile";
        }

        if(!RuntimeHelper.hasAppMeta(modifierList.getProject().getName()) || !SettingsStorageHelper.isEnableSideServer(modifierList.getProject())){
            return "disable_side_server";
        }
        // 获取文件的完整路径
        String filePath = virtualFile.getPath();
        String packageName = ((PsiJavaFile) psiFile).getPackageName();
        // 提取类名和包名部分
        String packageAndClassPath = filePath.substring(0, filePath.lastIndexOf('/'));
        String packagePath = packageAndClassPath.substring(0, packageAndClassPath.length() - ((PsiJavaFile) psiFile).getPackageName().replace(".", "/").length());
        // 检查路径是否以 src/test/java 结尾
        if (!packagePath.endsWith("src/test/java/")) {
            return "not_test_source";
        }

        String flexibleTestPackage = SettingsStorageHelper.getFlexibleTestPackage(modifierList.getProject());
        // 4. 必须在指定的包下
        return packageName.contains(flexibleTestPackage) ? null : "not_flexible_package";
    }
}