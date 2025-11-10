package com.testkit.tools.function_call;

import com.intellij.icons.AllIcons;
import com.testkit.RuntimeHelper;
import com.testkit.TestkitHelper;
import com.testkit.tools.ToolHelper;
import com.testkit.tools.flexible_test.FlexibleTestIconProvider;
import com.testkit.view.TestkitToolWindowFactory;
import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.notification.NotificationType;
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
import com.testkit.tools.PluginToolEnum;
import com.testkit.SettingsStorageHelper;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.Function;
import com.intellij.openapi.module.Module;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class FunctionCallIconProvider implements LineMarkerProvider {

    public static final Icon FUNCTION_CALL_ICON = IconLoader.getIcon("/icons/function-call.svg", FunctionCallIconProvider.class);

    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {
        return null; // 不再使用单个图标的方法
    }


    @Override
    public void collectSlowLineMarkers(List<? extends PsiElement> elements, Collection<? super LineMarkerInfo<?>> result) {
        for (PsiElement element : elements) {
            // 仅处理方法名标识符（叶子节点）
            if (element instanceof PsiIdentifier && element.getParent() instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) element.getParent();
                String beanMethod = isBeanMethod(method.getModifierList());
                if (beanMethod == null) {
                    result.addAll(createLineMarkers((PsiIdentifier) element));
                } else {
                    System.out.println("not_support_call, " + beanMethod + ":" + method.getContainingClass() + "#" + method.getName());
                }
            } else if (element instanceof PsiIdentifier && element.getParent() instanceof PsiClass) {
                // 类名标识符：为 Spring Bean 类添加测试生成按钮
                PsiClass psiClass = (PsiClass) element.getParent();
                String support = isCreateClassTest(psiClass);
                if (support == null) {
                    PsiIdentifier identifierElement = (PsiIdentifier) element;
                    LineMarkerInfo<PsiIdentifier> classTestMarker = new LineMarkerInfo<>(
                            identifierElement,
                            identifierElement.getTextRange(),
                            AllIcons.Nodes.TestSourceFolder,
                            new com.intellij.util.Function<PsiIdentifier, String>() {
                                @Override
                                public String fun(PsiIdentifier element) {
                                    return "Generate and open test class for this class";
                                }
                            },
                            new GutterIconNavigationHandler<PsiIdentifier>() {
                                @Override
                                public void navigate(java.awt.event.MouseEvent e, PsiIdentifier elt) {
                                    if (java.awt.GraphicsEnvironment.isHeadless()) {
                                        throw new java.awt.HeadlessException("Cannot display UI elements in a headless environment.");
                                    }
                                    Project project = elt.getProject();
                                    generateAndOpenTestClass(project, (PsiClass) elt.getParent());
                                }
                            },
                            GutterIconRenderer.Alignment.RIGHT
                    );
                    result.add(classTestMarker);
                } else {
                    System.out.println("not_support_createClassTest, " + support + ":" + psiClass.getQualifiedName());
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
            "java.util.LinkedHashMap",
            "java.util.TreeMap",
            "java.util.Collection",
            "java.util.Map"
    );

    private ArrayList<LineMarkerInfo<PsiIdentifier>> createLineMarkers(PsiIdentifier identifierElement) {
        PsiMethod psiMethod = (PsiMethod) identifierElement.getParent();
        ArrayList<LineMarkerInfo<PsiIdentifier>> lineMarkers = new ArrayList<>();
        String directInvoke = isDirectInvoke(psiMethod);
        if (directInvoke == null) {
            // 添加第一个图标
            LineMarkerInfo<PsiIdentifier> callMethodMarker = new LineMarkerInfo<>(
                    identifierElement,
                    identifierElement.getTextRange(),
                    FUNCTION_CALL_ICON,
                    new Function<PsiIdentifier, String>() {
                        @Override
                        public String fun(PsiIdentifier element) {
                            return "Call this function";
                        }
                    },
                    new GutterIconNavigationHandler<PsiIdentifier>() {
                        @Override
                        public void navigate(MouseEvent e, PsiIdentifier elt) {
                            if (GraphicsEnvironment.isHeadless()) {
                                throw new HeadlessException("Cannot display UI elements in a headless environment.");
                            }
                            Project project = elt.getProject();
                            TestkitToolWindowFactory.switch2Tool(project, PluginToolEnum.FUNCTION_CALL, elt.getParent());
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
            LineMarkerInfo<PsiIdentifier> generateTestMarker = new LineMarkerInfo<>(
                    identifierElement,
                    identifierElement.getTextRange(),
                    AllIcons.Nodes.TestSourceFolder,
                    new Function<PsiIdentifier, String>() {
                        @Override
                        public String fun(PsiIdentifier element) {
                            return "Generate and open test class for this method";
                        }
                    },
                    new GutterIconNavigationHandler<PsiIdentifier>() {
                        @Override
                        public void navigate(MouseEvent e, PsiIdentifier elt) {
                            if (GraphicsEnvironment.isHeadless()) {
                                throw new HeadlessException("Cannot display UI elements in a headless environment.");
                            }
                            Project project = elt.getProject();
                            generateAndOpenTestClass(project, (PsiMethod) elt.getParent());
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
        Module module = ModuleUtilCore.findModuleForPsiElement(psiMethod);
        if (module == null) {
            return "not_find_module";
        }
        if (!SettingsStorageHelper.isEnableSideServer(psiMethod.getProject())) {
            return "disable_side_server";
        }
        if (!psiMethod.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
            return "not_public";
        }

//        VirtualFile testSourceRoot = getTestSourceRoot(module);
//        if (testSourceRoot == null) {
//            return "not_find_test_source";
//        }
        return null;
    }

    private String isDirectInvoke(PsiMethod psiMethod) {
        if (FunctionCallIconProvider.isRequestMethod(psiMethod) || FunctionCallIconProvider.isSpringCacheMethod(psiMethod)) {
            return null;
        }
        boolean canInitparams = canInitparams(psiMethod);
        if (!canInitparams) {
            return "params_not_impl_class";
        }

        return null;
    }

    public static boolean canInitparams(PsiMethod psiMethod) {
        // 新增：检查所有参数都可以直接序列化
        for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
            PsiType parameterType = parameter.getType();
            PsiClass parameterClass = PsiUtil.resolveClassInType(parameterType);
            if (parameterClass == null) {
                continue;
            }

            // 放过 HttpServletRequest 及其子类
            if (ToolHelper.isHttpServletRequestType(parameterClass.getQualifiedName())) {
                continue;
            }

            // 判断是否是Collection或Map
            if (parameterClass.isEnum() || serializableClasses.contains(parameterClass.getQualifiedName())) {
                continue;
            }

            if (parameterClass.isInterface() || parameterClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return false;
            }
        }
        return true;
    }

    private String isBeanMethod(PsiModifierList modifierList) {
        if (modifierList==null) {
            return "npe";
        }
        // 1. 检查方法是否为 public
//        if (!modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
//            return "not_public";
//        }

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

        if (containingClass.isAnnotationType()) {
            return "annotationType";
        }

        // 4. 检查类是否含有 Spring 可注册 Bean 的注解
        if (!ToolHelper.isSpringBean(containingClass)) {
            return "not_spring_bean";
        }

        if (containingClass.isInterface() && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            return "interface_static";
        }


        if (!RuntimeHelper.hasAppMeta(psiMethod.getProject().getName())) {
            if (!FunctionCallIconProvider.isRequestMethod(psiMethod)) {
                return "no_app_meta";
            }
        }
        if (!SettingsStorageHelper.isEnableSideServer(psiMethod.getProject())) {
            if (!FunctionCallIconProvider.isRequestMethod(psiMethod)) {
                return "no_side_server";
            }
        }

        if (containsSpringInitializationMethod(containingClass, psiMethod)) {
            return "bean_method";
        }

        // 检查是否是main函数
        if ("main".equals(psiMethod.getName()) && psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
            return "main_method";
        }

        PsiFile psiFile = psiMethod.getContainingFile();
        // 5. 必须不在Java的test模块下
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
            // 获取文件的完整路径
            String filePath = virtualFile.getPath();
            // 检查路径是否以 src/test/java 结尾
            if (filePath.contains("/src/test/java/")) {
                return "is_test_module";
            }
        }


        return null;
    }

    private boolean containsSpringInitializationMethod(PsiClass psiClass, PsiMethod psiMethod) {
//        if (psiMethod.hasAnnotation("javax.annotation.PostConstruct")) {
//            return true;
//        }
        if (psiMethod.hasAnnotation("org.springframework.context.annotation.Bean")) {
            return true;
        }
//        if ("afterPropertiesSet".equals(psiMethod.getName()) && psiMethod.getParameterList().isEmpty()) {
//            return true;
//        }
//        if ("destroy".equals(psiMethod.getName()) && psiMethod.getParameterList().isEmpty()) {
//            return true;
//        }
        return false;
    }

    private void generateAndOpenTestClass(Project project, PsiMethod method) {
        Module module = ModuleUtilCore.findModuleForPsiElement(method);
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
                TestkitHelper.notify(module.getProject(), NotificationType.ERROR, "Failed to create test source root: " + e.getMessage());
                return;
            }
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            TestkitHelper.notify(project, NotificationType.ERROR, "Containing class not found for the " + method.getName() + " method.");
            return;
        }
        PsiDirectory testDir = PsiManager.getInstance(project).findDirectory(testSourceRoot);
        if (testDir == null) {
            TestkitHelper.notify(project, NotificationType.ERROR, "Test source root not found for the " + module.getName() + " module.");
            return;
        }

        generateOrAddTestMethod(method, project, testDir);
    }

    public static VirtualFile createTestRoot(Module module) throws IOException {
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

    public static VirtualFile getTestSourceRoot(Module module) {
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        for (VirtualFile sourceRoot : rootManager.getSourceRoots(true)) {
            if (sourceRoot.getPath().endsWith("/src/test/java")) {
                return sourceRoot;
            }
        }
        return null;
    }

    public static PsiDirectory createPackageDirectory(Project project, PsiDirectory testDir) {
        String flexibleTestPackage = SettingsStorageHelper.getFlexibleTestPackage(project);
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

    private static String generateTestClassContent(PsiMethod psiMethod, String packageName, String fieldName) {
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

    private static String generateTestClassContent(PsiClass psiClass, String packageName, String fieldName) {
        String className = psiClass.getName();
        String fullyQualifiedClassName = psiClass.getQualifiedName();
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

    public void generateOrAddTestMethod(PsiMethod psiMethod, Project project, PsiDirectory testSourceRoot) {
        String packageName = SettingsStorageHelper.getFlexibleTestPackage(project);
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
            if (testClass == null) {
                TestkitHelper.notify(project, NotificationType.ERROR, "Test class not found");
                return;
            }
        }

        // 查找已存在的测试方法
        String methodName1 = "test_" + methodName + "_" + psiMethod.getParameterList().getParameters().length;
        PsiMethod testMethod = findTestMethod(testClass, methodName1);

        if (testMethod == null) {
            // 如果测试方法不存在，添加新的测试方法
            addTestMethod(testClass, psiMethod, fieldName, psiMethod.getParameterList().getParameters().length);
            testMethod = findTestMethod(testClass, methodName1);
        }

        // 跳转到目标测试方法
        if (testMethod != null) {
            testMethod.navigate(true);
        } else {
            testClass.navigate(true);
        }
    }

    public static PsiClass findTestClass(String testClassName, String packageName, Project project) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        return JavaPsiFacade.getInstance(project).findClass(packageName + "." + testClassName, scope);
    }


    public static PsiMethod findTestMethod(PsiClass testClass, String methodName) {
        if (testClass == null) {
            return null;
        }
        for (PsiMethod method : testClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    public static PsiJavaFile createTestFile(PsiDirectory packageDir, String className, String content) {
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
            if (psiClass == null) {
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
            TestkitHelper.notify(project, NotificationType.WARNING, "copy this code to test class<br/>" + testMethodContent);
        }
    }

    public static boolean isFeign(PsiMethod method) {
        if (method == null) {
            return false;
        }
        return method.getContainingClass().hasAnnotation("org.springframework.cloud.openfeign.FeignClient");
    }

    public static boolean isRequestMethod(PsiMethod method) {
        if (method == null) {
            return false;
        }

        // Check if the method has any of the request mapping annotations
        String[] requestMethodAnnotations = new String[]{
                "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.GetMapping",
                "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PutMapping",
                "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PatchMapping"
        };

        for (String annotationFqn : requestMethodAnnotations) {
            if (method.getModifierList().findAnnotation(annotationFqn) != null) {
                PsiParameter[] parameters = method.getParameterList().getParameters();
                for (PsiParameter parameter : parameters) {
                    if (Objects.equals(parameter.getType().getCanonicalText(), "org.springframework.web.multipart.MultipartFile")) {
                        return false;
                    }
                }
                return true;
            }
        }

        // Check if the class has @RestController or @Controller annotation
//        PsiClass containingClass = method.getContainingClass();
//        if (containingClass != null) {
//            PsiAnnotation restControllerAnnotation = containingClass.getModifierList().findAnnotation("org.springframework.web.bind.annotation.RestController");
//            PsiAnnotation controllerAnnotation = containingClass.getModifierList().findAnnotation("org.springframework.stereotype.Controller");
//
//            if (restControllerAnnotation != null || controllerAnnotation != null) {
//                return true;
//            }
//        }

        return false;
    }


    public static boolean isSpringCacheMethod(PsiMethod method) {
        if (method == null) {
            return false;
        }

        PsiModifierList modifierList = method.getModifierList();
        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }

        return modifierList.hasAnnotation("org.springframework.cache.annotation.Cacheable") ||
                modifierList.hasAnnotation("org.springframework.cache.annotation.CacheEvict") ||
                modifierList.hasAnnotation("org.springframework.cache.annotation.CachePut") ||
                modifierList.hasAnnotation("org.springframework.cache.annotation.Caching");
    }

    private String isCreateClassTest(PsiClass psiClass) {
        if (psiClass == null || psiClass.getName() == null) {
            return "class_null";
        }
        if (psiClass.isInterface() || psiClass.isAnnotationType()) {
            return "not_concrete";
        }
        if (!ToolHelper.isSpringBean(psiClass)) {
            return "not_spring_bean";
        }
        PsiFile psiFile = psiClass.getContainingFile();
        if (psiFile == null || psiFile.getVirtualFile() == null) {
            return "no_virtual_file";
        }
        String filePath = psiFile.getVirtualFile().getPath();
        if (filePath.contains("/src/test/java/")) {
            return "is_test_source";
        }
        if (!RuntimeHelper.hasAppMeta(psiClass.getProject().getName())) {
            return "no_app_meta";
        }
        if (!SettingsStorageHelper.isEnableSideServer(psiClass.getProject())) {
            return "disable_side_server";
        }
        return null;
    }

    private void generateAndOpenTestClass(Project project, PsiClass psiClass) {
        Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
        if (module == null) {
            Messages.showMessageDialog(project, "Module not found for the selected class.", "Error", Messages.getErrorIcon());
            return;
        }

        VirtualFile testSourceRoot = getTestSourceRoot(module);
        if (testSourceRoot == null) {
            try {
                testSourceRoot = createTestRoot(module);
            } catch (Throwable e) {
                TestkitHelper.notify(module.getProject(), NotificationType.ERROR, "Failed to create test source root: " + e.getMessage());
                return;
            }
        }

        PsiDirectory testDir = PsiManager.getInstance(project).findDirectory(testSourceRoot);
        if (testDir == null) {
            TestkitHelper.notify(project, NotificationType.ERROR, "Test source root not found for the " + module.getName() + " module.");
            return;
        }

        String packageName = SettingsStorageHelper.getFlexibleTestPackage(project);
        String className = psiClass.getName();
        String testClassName = className + "_FlexibleTest";

        PsiClass exist = findTestClass(testClassName, packageName, project);
        if (exist != null) {
            String fieldName = Character.toLowerCase(className.charAt(0)) + className.substring(1);
            ensureAutowiredField(exist, psiClass, fieldName);
            exist.navigate(true);
            return;
        }

        String fieldName = Character.toLowerCase(className.charAt(0)) + className.substring(1);
        String content = generateTestClassContent(psiClass, packageName, fieldName);
        PsiDirectory packageDir = createPackageDirectory(project, testDir);
        PsiJavaFile testFile = createTestFile(packageDir, testClassName, content);
        PsiClass testClass = PsiTreeUtil.findChildOfType(testFile, PsiClass.class);
        if (testClass == null) {
            TestkitHelper.notify(project, NotificationType.ERROR, "Test class not found");
            return;
        }
        testClass.navigate(true);
    }

    private void ensureAutowiredField(PsiClass testClass, PsiClass targetClass, String fieldName) {
        Project project = testClass.getProject();
        PsiJavaFile javaFile = (PsiJavaFile) testClass.getContainingFile();
        // Check if an @Autowired field with the same type already exists
        for (PsiField field : testClass.getFields()) {
            PsiType type = field.getType();
            PsiClass resolved = PsiUtil.resolveClassInType(type);
            if (resolved != null && targetClass.getQualifiedName() != null && targetClass.getQualifiedName().equals(resolved.getQualifiedName())) {
                // Found same type field, no further action
                return;
            }
        }

        PsiElementFactory factory = PsiElementFactory.getInstance(project);

        // Ensure imports
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                if (javaFile.getImportList() != null) {
                    try {
                        PsiClass autowiredClass = JavaPsiFacade.getInstance(project).findClass("org.springframework.beans.factory.annotation.Autowired", GlobalSearchScope.allScope(project));
                        if (autowiredClass != null) {
                            javaFile.getImportList().add(factory.createImportStatement(autowiredClass));
                        }
                        if (targetClass != null) {
                            javaFile.getImportList().add(factory.createImportStatement(targetClass));
                        }
                    } catch (IncorrectOperationException ignored) {
                    }
                }

                // Create field text with simple class name after import
                String className = targetClass.getName();
                String fieldText = "@Autowired\nprivate " + className + " " + fieldName + ";";
                try {
                    PsiField newField = factory.createFieldFromText(fieldText, testClass);
                    testClass.add(newField);
                } catch (IncorrectOperationException ignored) {
                }
            }
        });
    }
}