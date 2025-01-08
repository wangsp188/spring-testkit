package com.fling.tools.call_method;

import com.fling.FlingHelper;
import com.fling.SettingsStorageHelper;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.openapi.module.Module;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

// action类实现
public class GenerateFlexibleTestAction extends AnAction {


    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent e) {
        // 1. 判断是否是java文件，控制右键菜单的显示
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        boolean isJavaFile = psiFile instanceof PsiJavaFile;
        if (!isJavaFile) {
            e.getPresentation().setVisible(false);
            return;
        }
        // 5. 必须不在Java的test模块下
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            e.getPresentation().setVisible(false);
            return;
        }
        // 获取文件的完整路径
        String filePath = virtualFile.getPath();
        // 检查路径是否以 src/test/java 结尾
        if (filePath.contains("/src/test/java/")) {
            e.getPresentation().setVisible(false);
            return;
        }

        // 检查是否有选中的文本
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean hasSelection = editor != null && editor.getSelectionModel().hasSelection();
        e.getPresentation().setVisible(hasSelection);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || !(psiFile instanceof PsiJavaFile)) {
            return;
        }

        // 获取选中的代码
        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) {
            return;
        }

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
        String packageName = SettingsStorageHelper.getFlexibleTestPackage(project);
        String methodName = "test_" + System.currentTimeMillis();
        String testClassName = className + "_SelectionTest";

        // 查找已存在的测试类
        WriteCommandAction.runWriteCommandAction(project, new Runnable() {
            @Override
            public void run() {
                PsiClass testClass = CallMethodIconProvider.findTestClass(testClassName, packageName, project);
                if (testClass == null) {
                    String classContent = generateTestClassContent(packageName, className);
                    PsiDirectory packageDir = CallMethodIconProvider.createPackageDirectory(project, testSourceRoot);
                    PsiJavaFile testFile = CallMethodIconProvider.createTestFile(packageDir, testClassName, classContent);
                    testClass = PsiTreeUtil.findChildOfType(testFile, PsiClass.class);
                    if (testClass == null) {
                        FlingHelper.notify(project, NotificationType.ERROR, "Test class not found");
                        return;
                    }
                }
                PsiElementFactory factory = PsiElementFactory.getInstance(project);
                PsiMethod method = factory.createMethodFromText(TEST_METHOD_TEMPLATE.formatted(methodName), testClass);
                testClass.add(method);
                PsiMethod testMethod = CallMethodIconProvider.findTestMethod(testClass, methodName);
                if (testMethod == null) {
                    testClass.navigate(true);
                } else {
                    testMethod.navigate(true);
                }
                FlingHelper.copyToClipboard(project, content, "The selected code has been copied");
            }
        });


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
