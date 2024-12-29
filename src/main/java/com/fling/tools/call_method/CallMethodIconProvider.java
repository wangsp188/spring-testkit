package com.fling.tools.call_method;

import com.fling.tools.ToolHelper;
import com.fling.tools.flexible_test.FlexibleTestIconProvider;
import com.fling.view.FlingToolWindowFactory;
import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.fling.tools.PluginToolEnum;
import com.fling.LocalStorageHelper;
import com.fling.FlingHelper;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public class CallMethodIconProvider implements LineMarkerProvider {

    public static final Icon CALL_METHOD_ICON = IconLoader.getIcon("/icons/spring-fling.svg", CallMethodIconProvider.class);

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
                String beanMethod = isBeanMethod(method.getModifierList());
                if (beanMethod == null) {
                    result.addAll(createLineMarkers(method));
                } else {
                    System.out.println("not_support_call, " + beanMethod + ":" + method.getContainingClass() + "#" + method.getName());
                }
            }
        }
    }

    // 集合和 Map 类的全名
    private static Set<String> serializableClasses = Set.of(
            "java.util.ArrayList",
            "java.util.List",
            "java.util.Set",
            "java.util.LinkedList",
            "java.util.HashSet",
            "java.util.LinkedHashSet",
            "java.util.TreeSet",
            "java.util.HashMap",
            "java.util.LinkedHashMcap",
            "java.util.TreeMap",
            "java.util.Collection"
    );

    private ArrayList<LineMarkerInfo<?>> createLineMarkers(PsiMethod psiMethod) {
        ArrayList<LineMarkerInfo<?>> lineMarkers = new ArrayList<>();


        String directInvoke = isDirectInvoke(psiMethod);
        if (directInvoke == null) {
            // 添加第一个图标
            LineMarkerInfo<PsiElement> callMethodMarker = new LineMarkerInfo<>(
                    psiMethod,
                    psiMethod.getTextRange(),
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
                            FlingToolWindowFactory.switch2Tool(project, PluginToolEnum.CALL_METHOD, elt);
                        }
                    },
                    GutterIconRenderer.Alignment.RIGHT
            );
            lineMarkers.add(callMethodMarker);
        } else {
            System.out.println("not_support_directInvoke," + directInvoke + ":" + psiMethod.getContainingClass() + "#" + psiMethod.getName());
        }

        String createTest = isCreateTest(psiMethod);
        if (createTest == null) {
            // 添加第二个图标
            LineMarkerInfo<PsiElement> generateTestMarker = new LineMarkerInfo<>(
                    psiMethod,
                    psiMethod.getTextRange(),
                    FlexibleTestIconProvider.FLEXIBLE_TEST_ICON,
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
        } else {
            System.out.println("not_support_createTest," + createTest + ":" + psiMethod.getContainingClass() + "#" + psiMethod.getName());
        }


        return lineMarkers;
    }

    private String isCreateTest(PsiMethod psiMethod) {
        com.intellij.openapi.module.@Nullable Module module = ModuleUtilCore.findModuleForPsiElement(psiMethod);
        if (module == null) {
            return "not_find_module";
        }

//        VirtualFile testSourceRoot = getTestSourceRoot(module);
//        if (testSourceRoot == null) {
//            return "not_find_test_source";
//        }
        return null;
    }

    private String isDirectInvoke(PsiMethod psiMethod) {
        // 新增：检查所有参数都可以直接序列化
        for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
            PsiType parameterType = parameter.getType();
            PsiClass parameterClass = PsiUtil.resolveClassInType(parameterType);
            if (parameterClass == null) {
                continue;
            }
            // 判断是否是Collection或Map
            if (parameterClass.isEnum() || serializableClasses.contains(parameterClass.getQualifiedName())) {
                continue;
            }

            if (parameterClass == null || parameterClass.isInterface() || parameterClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return "params_not_impl_class";
            }
        }


        return null;
    }

    private String isBeanMethod(@NotNull PsiModifierList modifierList) {
        // 1. 检查方法是否为 public
        if (!modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            return "not_public";
        }

        // 3. 获取方法和类
        PsiElement parent = modifierList.getParent();
        if (!(parent instanceof PsiMethod)) {
            return "not_method";
        }

        PsiMethod psiMethod = (PsiMethod) parent;

        if (psiMethod.isConstructor()) {
            return "constructor";
        }


        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null || containingClass.getName() == null) {
            return "containingClassNull";
        }

        // 4. 检查类是否含有 Spring 可注册 Bean 的注解
        if (!ToolHelper.isSpringBean(containingClass)) {
            return "not_spring_bean";
        }

        if (containsSpringInitializationMethod(containingClass, psiMethod)) {
            return "init_method";
        }

        // 检查是否是main函数
        if ("main".equals(psiMethod.getName()) && psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
            return "main_method";
        }

        PsiFile psiFile = psiMethod.getContainingFile();
        // 5. 必须不在Java的test模块下
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            return "not_find_virtualFile";
        }
        // 获取文件的完整路径
        String filePath = virtualFile.getPath();
        // 检查路径是否以 src/test/java 结尾
        if (filePath.contains("/src/test/java/")) {
            return "is_test_module";
        }

        return null;
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
            // 如果没有找到，尝试创建目录
            try {
                testSourceRoot = createTestRoot(module);
            } catch (Throwable e) {
                FlingHelper.notify(module.getProject(), NotificationType.ERROR, "Failed to create test source root: " + e.getMessage());
                return;
            }
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            FlingHelper.notify(project, NotificationType.WARNING, "Containing class not found for the " + method.getName() + " method.");
            return;
        }
        PsiDirectory testDir = PsiManager.getInstance(project).findDirectory(testSourceRoot);
        if (testDir == null) {
            FlingHelper.notify(project, NotificationType.WARNING, "Test source root not found for the " + module.getName() + " module.");
            return;
        }

        generateOrAddTestMethod(method, project, testDir);
    }

    private @NotNull VirtualFile createTestRoot(com.intellij.openapi.module.@NotNull Module module) throws IOException {
        return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
            @Override
            public VirtualFile compute() {
                try {
                    VirtualFile testSourceRoot;
                    VirtualFile baseDir = ModuleRootManager.getInstance(module).getContentRoots()[0]; // 获取模块的根目录
                    VirtualFile srcDir = baseDir.findChild("src");
                    if (srcDir == null) {
                        srcDir = baseDir.createChildDirectory(this, "src");
                    }

                    VirtualFile testDir = srcDir.findChild("test");
                    if (testDir == null) {
                        testDir = srcDir.createChildDirectory(this, "test");
                    }

                    testSourceRoot = testDir.findChild("java");
                    if (testSourceRoot == null) {
                        testSourceRoot = testDir.createChildDirectory(this, "java");
                    }
                    return testSourceRoot;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

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
            "public %s %s() throws Throwable{\n" +
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
            if (psiClass==null) {
                return psiType.getPresentableText();
            }
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
        String returnTypeInMethod = returnType.equals("void") ? "void" : "Object";

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
            FlingHelper.notify(project, NotificationType.WARNING, "copy this code to test class<br/>" + testMethodContent);
        }
    }
}