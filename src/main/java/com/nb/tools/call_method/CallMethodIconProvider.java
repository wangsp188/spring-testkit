package com.nb.tools.call_method;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.nb.tools.PluginToolEnum;
import com.nb.util.LocalStorageHelper;
import com.nb.view.WindowHelper;
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
import java.util.ArrayList;
import java.util.Collection;

public class CallMethodIconProvider implements LineMarkerProvider {

    private static final Icon CALL_METHOD_ICON = IconLoader.getIcon("/icons/no-bug.svg", CallMethodIconProvider.class);
    private static final Icon CALL_METHOD_CODER_ICON = IconLoader.getIcon("/icons/call-method.svg", CallMethodIconProvider.class);

    @Nullable
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        return null; // 不再使用单个图标的方法
    }


    @Override
    public void collectSlowLineMarkers(java.util.@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
        for (PsiElement element : elements) {
            if (element instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) element;
                if (isBeanMethod(method.getModifierList())) {
                    result.addAll(createLineMarkers(method));
                }else {
                    System.out.println("not_support_call:"+method.getContainingClass()+"#"+method.getName());
                }
            }
        }
    }

    private ArrayList<LineMarkerInfo<?>> createLineMarkers(PsiMethod method) {
        ArrayList<LineMarkerInfo<?>> lineMarkers = new ArrayList<>();

        // 添加第一个图标
        LineMarkerInfo<PsiElement> callMethodMarker = new LineMarkerInfo<>(
                method,
                method.getTextRange(),
                CALL_METHOD_ICON,
                new Function<PsiElement, String>() {
                    @Override
                    public String fun(PsiElement element) {
                        return "Call this method";
                    }
                },
                new GutterIconNavigationHandler<PsiElement>() {
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
        lineMarkers.add(callMethodMarker);

        // 添加第二个图标
        LineMarkerInfo<PsiElement> generateTestMarker = new LineMarkerInfo<>(
                method,
                method.getTextRange(),
                CALL_METHOD_CODER_ICON,
                new Function<PsiElement, String>() {
                    @Override
                    public String fun(PsiElement element) {
                        return "Generate and open test class for this method";
                    }
                },
                new GutterIconNavigationHandler<PsiElement>() {
                    @Override
                    public void navigate(MouseEvent e, PsiElement elt) {
                        if (GraphicsEnvironment.isHeadless()) {
                            throw new HeadlessException("Cannot display UI elements in a headless environment.");
                        }
                        Project project = elt.getProject();
                        generateAndOpenTestClass(project, (PsiMethod) elt);
                    }
                },
                GutterIconRenderer.Alignment.RIGHT
        );
        lineMarkers.add(generateTestMarker);

        return lineMarkers;
    }

    private boolean isBeanMethod(@NotNull PsiModifierList modifierList) {
        // 1. 检查方法是否为 public
        if (!modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        // 3. 获取方法和类
        PsiElement parent = modifierList.getParent();
        if (!(parent instanceof PsiMethod)) {
            return false;
        }
        PsiMethod psiMethod = (PsiMethod) parent;

        if (psiMethod.isConstructor()) {
            return false;
        }


        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        // 4. 检查类是否含有 Spring 可注册 Bean 的注解
        if (!isSpringBean(containingClass)) {
            return false;
        }

        if (containsSpringInitializationMethod(containingClass, psiMethod)) {
            return false;
        }


        com.intellij.openapi.module.@Nullable Module module = ModuleUtilCore.findModuleForPsiElement(psiMethod);
        if (module == null) {
            return false;
        }

        VirtualFile testSourceRoot = getTestSourceRoot(module);
        if (testSourceRoot == null) {
            return false;
        }

        PsiFile psiFile = psiMethod.getContainingFile();
        if (!(psiFile instanceof PsiJavaFile)) {
            return false;
        }

        // 5. 必须不在Java的test模块下
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            return false;
        }
        // 获取文件的完整路径
        String filePath = virtualFile.getPath();
        // 检查路径是否以 src/test/java 结尾
        if (filePath.contains("/src/test/java/")) {
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
                return !psiClass.hasAnnotation("org.aspectj.lang.annotation.Aspect");
            }
        }

        return false;
    }

    private boolean containsSpringInitializationMethod(@NotNull PsiClass psiClass, PsiMethod psiMethod) {
        if (psiMethod.hasAnnotation("javax.annotation.PostConstruct")) {
            return true;
        }
        if (psiMethod.hasAnnotation("org.springframework.context.annotation.Bean")) {
            return true;
        }
        if ("afterPropertiesSet".equals(psiMethod.getName()) && psiMethod.getParameterList().isEmpty()) {
            return true;
        }
        if ("destroy".equals(psiMethod.getName()) && psiMethod.getParameterList().isEmpty()) {
            return true;
        }
        return false;
    }

    private void generateAndOpenTestClass(Project project, PsiMethod method) {
        com.intellij.openapi.module.@Nullable Module module = ModuleUtilCore.findModuleForPsiElement(method);
        if (module == null) {
            Messages.showMessageDialog(project, "Module not found for the selected method.", "Error", Messages.getErrorIcon());
            return;
        }

        VirtualFile testSourceRoot = getTestSourceRoot(module);
        if (testSourceRoot == null) {
            Notification notification = new Notification("No-Bug", "Warn", "Test source root not found for the " + module.getName() + " module.", NotificationType.WARNING);
            Notifications.Bus.notify(notification, project);
            return;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            Notification notification = new Notification("No-Bug", "Warn", "Containing class not found for the " + method.getName() + " method.", NotificationType.WARNING);
            Notifications.Bus.notify(notification, project);
            return;
        }
        PsiDirectory testDir = PsiManager.getInstance(project).findDirectory(testSourceRoot);
        if (testDir == null) {
            Notification notification = new Notification("No-Bug", "Warn", "Test source root not found for the " + module.getName() + " module.", NotificationType.WARNING);
            Notifications.Bus.notify(notification, project);
            return;
        }

        generateOrAddTestMethod(method, project, testDir);
    }

    private VirtualFile getTestSourceRoot(com.intellij.openapi.module.@Nullable Module module) {
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        for (VirtualFile sourceRoot : rootManager.getSourceRoots(true)) {
            if (sourceRoot.getPath().endsWith("/src/test/java")) {
                return sourceRoot;
            }
        }
        return null;
    }

    private PsiDirectory createPackageDirectory(Project project, PsiDirectory testDir) {
        String flexibleTestPackage = LocalStorageHelper.getFlexibleTestPackage(project);
        String[] packageParts = flexibleTestPackage.split("\\.");


        return ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
            @Override
            public PsiDirectory compute() {
                PsiDirectory currentDir = testDir;

                for (String part : packageParts) {
                    PsiDirectory subDir = currentDir.findSubdirectory(part);
                    if (subDir == null) {
                        subDir = currentDir.createSubdirectory(part);
                    }
                    currentDir = subDir;
                }

                return currentDir;
            }
        });

    }

    private static final String TEST_CLASS_TEMPLATE =
            "package %s;\n\n" +
                    "import org.springframework.beans.factory.annotation.Autowired;\n" +
                    "%s\n\n" +  // 导入类的全类名
                    "public class %s {\n\n" +
                    "    @Autowired\n" +
                    "    private %s %s;\n\n" +
                    "}\n";

    private static final String TEST_METHOD_TEMPLATE =
            "public %s %s() {\n" +
                    "%s" +
                    "$_1_$%s;\n" +
                    "}\n";

    public String generateTestClassContent(@NotNull PsiMethod psiMethod, @NotNull String packageName, String fieldName) {
        PsiClass containingClass = psiMethod.getContainingClass();
        String className = containingClass.getName();
        String fullyQualifiedClassName = containingClass.getQualifiedName();
        String testClassName = className + "_FlexibleTest";
        String importStatement = "import " + fullyQualifiedClassName + ";";

        return String.format(TEST_CLASS_TEMPLATE,
                packageName,
                importStatement,
                testClassName,
                className,
                fieldName);
    }

    private String getFullyQualifiedName(PsiType psiType) {
        if (psiType == null) {
            return "void";
        }
        if (psiType instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) psiType;
            return classType.getCanonicalText();
        } else {
            return psiType.getCanonicalText();
        }
    }

    public void generateOrAddTestMethod(@NotNull PsiMethod psiMethod, @NotNull Project project, @NotNull PsiDirectory testSourceRoot) {
        String packageName = LocalStorageHelper.getFlexibleTestPackage(project);
        String methodName = psiMethod.getName();
        PsiClass containingClass = psiMethod.getContainingClass();
        String className = containingClass.getName();
        String testClassName = className + "_FlexibleTest";

        // 查找已存在的测试类
        PsiClass testClass = findTestClass(testClassName, packageName, project);
        String fieldName = Character.toLowerCase(className.charAt(0)) + className.substring(1);
        if (testClass == null) {
            // 如果测试类不存在，创建新的测试类
            String content = generateTestClassContent(psiMethod, packageName, fieldName);
            PsiDirectory packageDir = createPackageDirectory(project, testSourceRoot);
            PsiJavaFile testFile = createTestFile(packageDir, testClassName, content);
            testClass = PsiTreeUtil.findChildOfType(testFile, PsiClass.class);
        }

        // 查找已存在的测试方法
        PsiMethod testMethod = findTestMethod(testClass, methodName, psiMethod.getParameterList().getParameters().length);

        if (testMethod == null) {
            // 如果测试方法不存在，添加新的测试方法
            addTestMethod(testClass, psiMethod, fieldName, psiMethod.getParameterList().getParameters().length);
        }

        // 跳转到目标测试方法
        if (testMethod != null) {
            testMethod.navigate(true);
        } else {
            testClass.navigate(true);
        }
    }

    private PsiClass findTestClass(String testClassName, String packageName, Project project) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        return JavaPsiFacade.getInstance(project).findClass(packageName + "." + testClassName, scope);
    }


    private PsiMethod findTestMethod(PsiClass testClass, String methodName, int length) {
        if (testClass == null) {
            return null;
        }
        for (PsiMethod method : testClass.getMethods()) {
            if (method.getName().equals("test_" + methodName + "_" + length)) {
                return method;
            }
        }
        return null;
    }

    private PsiJavaFile createTestFile(PsiDirectory packageDir, String className, String content) {
        return ApplicationManager.getApplication().runWriteAction(new Computable<PsiJavaFile>() {

                                                                      @Override
                                                                      public PsiJavaFile compute() {
                                                                          PsiFileFactory factory = PsiFileFactory.getInstance(packageDir.getProject());
                                                                          PsiJavaFile file = (PsiJavaFile) factory.createFileFromText(className + ".java", JavaFileType.INSTANCE, content);
                                                                          return (PsiJavaFile) packageDir.add(file);
                                                                      }
                                                                  }
        );

    }

    private void addTestMethod(PsiClass testClass, PsiMethod psiMethod, String fieldName, int length) {
        Project project = psiMethod.getProject();

        PsiElementFactory factory = PsiElementFactory.getInstance(project);
        PsiJavaFile javaFile = (PsiJavaFile) testClass.getContainingFile();

        Function<PsiType, String> getTypeName = psiType -> {
            if (psiType == null) {
                return "void";
            }

            String qualifiedName = getFullyQualifiedName(psiType);
            if ("void".equals(qualifiedName)) {
                return "void";
            }
            if (javaFile.getImportList() != null) {
                for (PsiImportStatement importStatement : javaFile.getImportList().getImportStatements()) {
                    if (importStatement.getQualifiedName().endsWith("*")) {
                        if (qualifiedName.startsWith(importStatement.getQualifiedName().substring(0, importStatement.getQualifiedName().length() - 1))) {
                            return psiType.getPresentableText();
                        }
                    }
                    if (qualifiedName.equals(importStatement.getQualifiedName())) {
                        return psiType.getPresentableText();
                    }
                }
            }
            PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
            PsiImportStatement psiImportStatement = ApplicationManager.getApplication().runWriteAction(new Computable<PsiImportStatement>() {
                @Override
                public PsiImportStatement compute() {
                    try {
                        PsiImportStatement importStatement = factory.createImportStatement(psiClass);
                        javaFile.getImportList().add(importStatement);
                        return importStatement;
                    } catch (IncorrectOperationException e) {
                        e.printStackTrace();
                        System.err.println("Failed to create import: " + e.getMessage());
                        return null;
                    }
                }
            });
            return psiImportStatement == null ? qualifiedName : psiClass.getName();
        };

        String returnType = getTypeName.fun(psiMethod.getReturnType());
        String methodName = psiMethod.getName();

        // 获取 psiMethod 的参数
        PsiParameter[] parameters = psiMethod.getParameterList().getParameters();

        // 初始化方法调用的参数
        StringBuilder methodCallArgs = new StringBuilder();
        StringBuilder initStatements = new StringBuilder();

        for (PsiParameter parameter : parameters) {
            String paramType = getTypeName.fun(parameter.getType());
            String paramName = parameter.getName();

            // 为每个参数创建一个默认值
            String defaultValue;
            if (paramType.equals("int")) {
                defaultValue = "0"; // 整型参数默认值
            } else if (paramType.equals("double")) {
                defaultValue = "0.0"; // 浮点型参数默认值
            } else if (paramType.equals("boolean")) {
                defaultValue = "false"; // 布尔类型参数默认值
            } else if (paramType.equals("String") || paramType.equals("java.lang.String")) {
                defaultValue = "\"\""; // 字符串类型参数默认值
            } else {
                defaultValue = "null"; // 其他类型的默认值
            }

            // 为每个参数拼接初始化语句
            initStatements.append("$_1_$").append(paramType).append(" ").append(paramName).append(" = ").append(defaultValue).append(";\n");
            methodCallArgs.append(paramName).append(","); // 拼接方法调用参数
        }

        // 去掉最后一个逗号和空格
        if (methodCallArgs.length() > 0) {
            methodCallArgs.setLength(methodCallArgs.length() - 1);
        }

        String fullMethodCall = String.format("%s.%s(%s)", fieldName, methodName, methodCallArgs);

        // Determine if the method is void and adjust the template accordingly
        String returnStatement = returnType.equals("void") ? "" : "return ";
        String returnTypeInMethod = returnType.equals("void") ? "void" : returnType;

        // 创建测试方法内容
        String testMethodContent = String.format(TEST_METHOD_TEMPLATE,
                returnTypeInMethod,
                "test_" + methodName + "_" + length,
                initStatements, returnStatement + fullMethodCall);

        String space = "";
        CodeStyleSettings settings = CodeStyle.getSettings(psiMethod.getProject());
        // 判断是否使用制表符
        if (settings.useTabCharacter(JavaFileType.INSTANCE)) {
            space = "\t";
        } else {
            int indentSize = settings.getIndentSize(JavaFileType.INSTANCE);
            for (int i = 0; i < indentSize; i++) {
                space += " ";
            }
        }
        testMethodContent = testMethodContent.replace("$_1_$", space);

        try {
            // 创建新方法并添加到测试类
            PsiMethod newMethod = factory.createMethodFromText(testMethodContent, testClass);
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                    testClass.add(newMethod);
                }
            });
        } catch (IncorrectOperationException e) {
            e.printStackTrace();
            System.err.println("Failed to create method: " + e.getMessage());
            Notification notification = new Notification("No-Bug", "Info", "copy this code to test class\n" + testMethodContent, NotificationType.WARNING);
            Notifications.Bus.notify(notification, project);
        }
    }
}