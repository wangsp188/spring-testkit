package com.fling.tools.call_method;

import com.fling.FlingHelper;
import com.fling.LocalStorageHelper;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.openapi.module.Module;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

// action类实现
public class GenerateFlexibleTestAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 1. 判断是否是java文件，控制右键菜单的显示
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        boolean isJavaFile = psiFile instanceof PsiJavaFile;

        // 检查是否有选中的文本
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean hasSelection = editor != null && editor.getSelectionModel().hasSelection();

        e.getPresentation().setVisible(isJavaFile && hasSelection);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || !(psiFile instanceof PsiJavaFile)) return;

        // 获取选中的代码
        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) return;

        // 获取当前类名
        String className = ((PsiJavaFile) psiFile).getClasses()[0].getName();

        Module module = ModuleUtil.findModuleForPsiElement(psiFile);
        if (module == null) {
            FlingHelper.notify(project, NotificationType.ERROR, "Cannot find module for current file");
            return;
        }
        generateAndOpenTestClass(project, module, className, selectedText);
    }

    private void generateAndOpenTestClass(Project project, Module module, String className, String content) {

        VirtualFile testSourceRoot = CallMethodIconProvider.getTestSourceRoot(module);
        if (testSourceRoot == null) {
            // 如果没有找到，尝试创建目录
            try {
                testSourceRoot = CallMethodIconProvider.createTestRoot(module);
            } catch (Throwable e) {
                FlingHelper.notify(module.getProject(), NotificationType.ERROR, "Failed to create test source root: " + e.getMessage());
                return;
            }
        }


        PsiDirectory testDir = PsiManager.getInstance(project).findDirectory(testSourceRoot);
        if (testDir == null) {
            FlingHelper.notify(project, NotificationType.ERROR, "Test source root not found for the " + module.getName() + " module.");
            return;
        }
        generateOrAddTestMethod(project, testDir, className, content);
    }

    public void generateOrAddTestMethod(Project project, PsiDirectory testSourceRoot, String className, String content) {
        String packageName = LocalStorageHelper.getFlexibleTestPackage(project);
        String methodName = "test_" + System.currentTimeMillis();
        String testClassName = className + "_SelectionTest";

        // 查找已存在的测试类
        PsiClass testClass = CallMethodIconProvider.findTestClass(testClassName, packageName, project);
        if (testClass == null) {
            // 如果测试类不存在，创建新的测试类
            String classContent = generateTestClassContent(packageName, className);
            PsiDirectory packageDir = CallMethodIconProvider.createPackageDirectory(project, testSourceRoot);
            PsiJavaFile testFile = CallMethodIconProvider.createTestFile(packageDir, testClassName, classContent);
            testClass = PsiTreeUtil.findChildOfType(testFile, PsiClass.class);
            if (testClass == null) {
                FlingHelper.notify(project, NotificationType.ERROR, "Test class not found");
                return;
            }
        }

        testClass.navigate(false);
        FlingHelper.copyToClipboard(project, content, "The selected code has been copied");
    }

    private static final String TEST_CLASS_TEMPLATE =
            "package %s;\n\n" +
                    "public class %s {\n\n" +
                    "}\n";

    private static final String TEST_METHOD_TEMPLATE = "public Object %s() throws Throwable{\n" +
            "            //The selected code has been copied\n" +
            "            return null;\n" +
            "}\n";

    private static String generateTestClassContent(String packageName, String className) {
        String testClassName = className + "_SelectionTest";
        return String.format(TEST_CLASS_TEMPLATE,
                packageName,
                testClassName
        );
    }

}
