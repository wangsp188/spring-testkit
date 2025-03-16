package com.testkit.side_server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import javax.tools.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReflexUtils {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        // 注册 Date 反序列化器
        module.addDeserializer(Date.class, new CustomDateDeserializer());
        // 配置 ObjectMapper 在序列化时包含 null 值
        MAPPER.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        // 自动处理枚举类型，无需自定义注册
        MAPPER.registerModule(module);
    }

    private static JdkDynamicCompiler compiler = new JdkDynamicCompiler();


    public static Class compile(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return compiler.compile(code);
    }


    public static void autowireBean(AutowireCapableBeanFactory beanFactory, Object instance) {
        if (instance == null || beanFactory == null) {
            return;
        }
        beanFactory.autowireBean(instance);
        // Loop through all fields in the bean
        Class<?> aClass = instance.getClass();
        for (Field field : aClass.getDeclaredFields()) {
            // Utility method to make the field accessible if it's private/protected
            ReflectionUtils.makeAccessible(field);
            try {
                Object old = field.get(instance);
                if (old != null) {
                    continue;
                }
            } catch (IllegalAccessException e) {
                continue;
            }
            // Check if the field has the @Autowired annotation
            if (field.isAnnotationPresent(Autowired.class)) {
                // Define your own logic to create an instance for the field
                try {
                    Object fieldInstance = beanFactory.getBean(field.getType());
                    // Set the field with the instance
                    field.set(instance, fieldInstance);
                } catch (Throwable e) {
                    throw new RuntimeException("@Autowired inject:" + field.getName() + " error", e);
                }
            } else if (field.isAnnotationPresent(Qualifier.class)) {
                try {
                    Qualifier qualifier = field.getAnnotation(Qualifier.class);
                    String name = null;
                    if (!qualifier.value().isEmpty()) {
                        name = qualifier.value();
                    } else {
                        name = field.getName();
                    }

                    Object fieldInstance = beanFactory.getBean(name);
                    // Set the field with the instance
                    field.set(instance, fieldInstance);
                } catch (Throwable e) {
                    throw new RuntimeException("@Qualifier inject:" + field.getName() + " error", e);
                }
            }

            Class<? extends Annotation> resourceType = null;
            try {
                resourceType = (Class<? extends Annotation>) Class.forName("jakarta.annotation.Resource");
            } catch (ClassNotFoundException e) {
                try {
                    resourceType = (Class<? extends Annotation>) Class.forName("javax.annotation.Resource");
                } catch (ClassNotFoundException ex) {
                }
            }
            if (resourceType == null) {
                continue;
            }

            if (!field.isAnnotationPresent(resourceType)) {
                continue;
            }
            Annotation resourceAnnotation = field.getAnnotation(resourceType);
            //反射获取它的name()属性
            //如果name为空使用字段名尝试获取bean
            //如果name获取不到则使用type获取
            // 使用反射获取 @Resource 注解的 name() 属性值
            String name = null;
            try {
                Method nameMethod = resourceType.getMethod("name"); // 获取 name() 方法
                name = (String) nameMethod.invoke(resourceAnnotation); // 调用 name() 方法
            } catch (Exception e) {
                throw new RuntimeException("Failed to get name from @Resource annotation with " + field.getName() + ".", e);
            }

            try {
                Object bean = null;
                // 1. 按 name 属性查找
                if (name == null || name.isEmpty()) {
                    name = field.getName();
                }
                if (beanFactory.containsBean(name)) {
                    bean = beanFactory.getBean(name);
                }else{
                    bean = beanFactory.getBean(field.getType());
                }
                field.set(instance, bean);
            } catch (Exception e) {
                throw new RuntimeException("Failed @Resource to inject field: " + field.getName(), e);
            }
        }
    }


    public static ReflexBox parse(Class typeClass, String methodName, String methodArgTypesStr, String methodArgsStr) throws JsonProcessingException, NoSuchMethodException, ClassNotFoundException {
        TypeFactory typeFactory = MAPPER.getTypeFactory();

        List<String> methodArgTypesList = MAPPER.readValue(methodArgTypesStr, new TypeReference<List<String>>() {
        });
        // 创建 Class<?>[] 数组
        JavaType[] methodArgTypes = new JavaType[methodArgTypesList.size()];
        for (int i = 0; i < methodArgTypesList.size(); i++) {
            methodArgTypes[i] = typeFactory.constructFromCanonical(methodArgTypesList.get(i));
        }

        // 获取方法
        Method method = findMethod(typeClass, methodName, methodArgTypes);
        method.setAccessible(true);

        Object[] methodArgsJson = MAPPER.readValue(methodArgsStr, Object[].class);
        // 根据方法参数类型将 JSON 字符串解析为对应类型的对象
        Object[] methodArgs = new Object[methodArgTypes.length];
        for (int i = 0; i < methodArgTypes.length; i++) {
            try {
                JavaType argType = methodArgTypes[i];
                Object argJson = methodArgsJson[i];
                if (argType.hasRawClass(String.class)) {
                    methodArgs[i] = argJson;
                } else if (argType.hasRawClass(Integer.class) || argType.isPrimitive() && argType.getRawClass() == int.class) {
                    methodArgs[i] = argJson == null || argJson.toString().isEmpty() ? (argType.getRawClass() == int.class ? 0 : null) : Integer.valueOf(Integer.parseInt(argJson.toString()));
                } else if (argType.hasRawClass(Long.class) || argType.isPrimitive() && argType.getRawClass() == long.class) {
                    methodArgs[i] = argJson == null || argJson.toString().isEmpty() ? (argType.getRawClass() == long.class ? 0L : null) : Long.valueOf(Long.parseLong(argJson.toString()));
                } else if (argType.hasRawClass(Double.class) || argType.isPrimitive() && argType.getRawClass() == double.class) {
                    methodArgs[i] = argJson == null || argJson.toString().isEmpty() ? (argType.getRawClass() == double.class ? 0.0d : null) : Double.valueOf(Double.parseDouble(argJson.toString()));
                } else if (argType.hasRawClass(Boolean.class) || argType.isPrimitive() && argType.getRawClass() == boolean.class) {
                    methodArgs[i] = argJson == null || argJson.toString().isEmpty() ? (argType.getRawClass() == boolean.class ? false : null) : Boolean.valueOf(Boolean.parseBoolean(argJson.toString()));
                } else if (argType.hasRawClass(Date.class)) {
                    //如果是数字就按照lang来转
                    if (argJson != null && argJson.toString().trim().matches("\\d+")) {
                        methodArgs[i] = new Date(Long.parseLong(argJson.toString()));
                    } else {
                        try {
                            methodArgs[i] = argJson == null || argJson.toString().trim().isEmpty() ? null : dateFormat.parse(argJson.toString().trim());
                        } catch (ParseException e) {
                            throw new TestkitException("date need match yyyy-MM-dd HH:mm:ss or ms timestamp");
                        }
                    }
                } else if (argType.isEnumType()) {
                    methodArgs[i] = argJson == null || argJson.toString().isEmpty() ? null : Enum.valueOf(argType.getRawClass().asSubclass(Enum.class), argJson.toString());
                } else {
                    // 其他类型
                    methodArgs[i] = MAPPER.readValue(MAPPER.writeValueAsString(argJson), argType);
                }
            } catch (Throwable e) {
                throw new TestkitException("can not deserialization param,  index:" + i + ", " + e);
            }
        }
        return new ReflexBox(method, methodArgs);
    }

    private static Method findMethod(Class<?> typeClass, String methodName, JavaType[] methodArgTypes) throws NoSuchMethodException, ClassNotFoundException {
        try {
            Class<?>[] parameterTypes = new Class<?>[methodArgTypes.length];
            for (int i = 0; i < methodArgTypes.length; i++) {
                parameterTypes[i] = loadClass(methodArgTypes[i]);
            }
            return typeClass.getDeclaredMethod(methodName, parameterTypes);
        } catch (Throwable e) {
            throw new TestkitException("can not find method " + methodName + " in " + typeClass + ", " + e);
        }
    }

    /**
     * Helping method to load class considering generic type
     */
    private static Class<?> loadClass(JavaType javaType) throws ClassNotFoundException {
        // Handle raw class and array types
        if (javaType.isContainerType()) {
            // For collection or map types, return raw class type
            return javaType.getRawClass();
        }
        // 检查基本类型
        String canonicalName = javaType.toCanonical();
        switch (canonicalName) {
            case "boolean":
                return boolean.class;
            case "byte":
                return byte.class;
            case "char":
                return char.class;
            case "short":
                return short.class;
            case "int":
                return int.class;
            case "long":
                return long.class;
            case "float":
                return float.class;
            case "double":
                return double.class;
            case "void":
                return void.class;
            default:
                break; // 非基本类型，继续执行
        }

        return javaType.getRawClass();
        // 返回普通类
//        return Class.forName(canonicalName);
    }


    public static boolean isAopProxy(@Nullable Object object) {
        return (object instanceof SpringProxy && (Proxy.isProxyClass(object.getClass()) ||
                object.getClass().getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)));
    }

    public static boolean isCglibProxy(@Nullable Object object) {
        return (object instanceof SpringProxy &&
                object.getClass().getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR));
    }

    public static boolean isJdkDynamicProxy(@Nullable Object object) {
        return (object instanceof SpringProxy && Proxy.isProxyClass(object.getClass()));
    }

    /**
     * 递归获取代理对象中的原始对象。
     *
     * @param proxy 代理对象
     * @return 原始对象
     */
    public static Object getTargetObject(Object proxy) {
        if (proxy == null) {
            return null;
        }

        if (isAopProxy(proxy)) {
            try {
                if (isJdkDynamicProxy(proxy)) {
                    Advised advised = (Advised) proxy;
                    Object target = advised.getTargetSource().getTarget();
                    if (target == null) {
                        return proxy; // 如果没有目标对象，返回原代理对象
                    }
                    // 递归获取最底层的目标对象
                    return getTargetObject(target);
                } else if (isCglibProxy(proxy)) {
                    // CGLIB 代理对象
                    Field hField = proxy.getClass().getDeclaredField("CGLIB$CALLBACK_0");
                    hField.setAccessible(true);
                    Object dynamicAdvisedInterceptor = hField.get(proxy);
                    Field advised = dynamicAdvisedInterceptor.getClass().getDeclaredField("advised");
                    advised.setAccessible(true);
                    Object targetSource = advised.get(dynamicAdvisedInterceptor);
                    Object target = ((Advised) targetSource).getTargetSource().getTarget();
                    if (target == null) {
                        return proxy; // 如果没有目标对象，返回原代理对象
                    }
                    // 递归获取最底层的目标对象
                    return getTargetObject(target);
                }
            } catch (Exception e) {
                throw new TestkitException("Could not obtain target object from proxy");
            }
        }
        return proxy; // 如果不是代理对象，直接返回原对象
    }


    // 自定义 Date 反序列化
    public static class CustomDateDeserializer extends JsonDeserializer<Date> {
        @Override
        public Date deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            String date = p.getText();
            try {
                //如果是数字就按照lang来转
                if (date.matches("\\d+")) {
                    return new Date(Long.parseLong(date));
                }
                return dateFormat.parse(date);
            } catch (ParseException e) {
                throw new TestkitException("Date parse error: " + date);
            }
        }
    }


    /**
     * jdk dynamic compiler
     * Thread safety
     *
     * @Author shaopeng
     * @Date 2021/11/30
     * @Version 1.0
     * @see JdkDynamicCompiler#compile
     */
    public static class JdkDynamicCompiler {


        /**
         * copyFrom com.alibaba.dubbo.common.compiler.support.AbstractCompiler
         */
        private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([$_a-zA-Z][$_a-zA-Z0-9.]*);");
        /**
         * copyFrom com.alibaba.dubbo.common.compiler.support.AbstractCompiler
         */
        private static final Pattern CLASS_PATTERN = Pattern.compile("class\\s+([$_a-zA-Z][$_a-zA-Z0-9]*)\\s+");


        /**
         *
         */
        private final JavaCompiler compiler;

        /**
         *
         */
        private final ClassLoader parentClassLoader;

        /**
         * compile source classpath
         */
        private final String classpath;

        /**
         * code encoding
         */
        private String encoding = "UTF-8";

        public JdkDynamicCompiler() {
            compiler = ToolProvider.getSystemJavaCompiler();
            this.parentClassLoader = this.getClass().getClassLoader();
            this.classpath = System.getProperty("java.class.path");
        }

        /**
         * compile code to class
         * Each compilation will be a new classLoader,
         * so a piece of code can be repeated compilation of multiple calls to get more than one class of the same name (loader is not the same)
         *
         * @param code
         * @return
         */
        public synchronized Class<?> compile(String code) throws CompileException {
            if (null == compiler) {
                throw new CompileException("can not find jdk, please check");
            }
            Matcher matcher = PACKAGE_PATTERN.matcher(code);
            String packageName = null;
            if (matcher.find()) {
                packageName = matcher.group(1);
            }
            matcher = CLASS_PATTERN.matcher(code);
            String simpleName;
            if (matcher.find()) {
                simpleName = matcher.group(1);
            } else {
                throw new CompileException("not find class name, please check");
            }
            String className;
            if (packageName != null && !packageName.isEmpty()) {
                className = packageName + "." + simpleName;
            } else {
                className = simpleName;
            }
            try {
                //use parent first
                parentClassLoader.loadClass(className);
                throw new CompileException("className:" + className + " is in exist in " + parentClassLoader + ", please change another");
            } catch (ClassNotFoundException ignore) {
                //parent is fail then self compile
                return doCompile(className, code);
            }
        }

        private Class<?> doCompile(String className, String javaCode) throws CompileException {
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager standardJavaFileManager = compiler.getStandardFileManager(diagnostics, null, null);
            MemoryFileManager fileManager = new MemoryFileManager(standardJavaFileManager);
            StringJavaFileObject file = new StringJavaFileObject(className, javaCode);
            DynamicClassLoader dynamicClassLoader = null;
            try {
                List<JavaFileObject> files = new ArrayList<>();
                files.add(file);
                List<String> options = new ArrayList<>();
                options.add("-encoding");
                options.add(encoding);
                options.add("-classpath");
                options.add(this.classpath);
                JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, files);
                if (!task.call()) {
                    throw new CompileException("Compilation failed. class: " + className + ", detail:" + detailErrorDiagnostics(diagnostics, file));
                }
                dynamicClassLoader = new DynamicClassLoader(this.parentClassLoader);
                return defineCls(fileManager, dynamicClassLoader);
            } finally {
                try {
                    if (dynamicClassLoader != null) {
                        dynamicClassLoader.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("dynamicClassLoaderCloseThrows");
                }
                try {
                    file.delete();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("fileDeleteThrows");
                }
                try {
                    fileManager.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("fileManagerCloseThrows");
                }
            }
        }

        private Class<?> defineCls(MemoryFileManager fileManager, DynamicClassLoader dynamicClassLoader) {
            for (JavaClassObject javaClassObject : fileManager.getInnerClassJavaClassObject()) {
                dynamicClassLoader.defineClass(javaClassObject);
            }
            JavaClassObject mainClass = fileManager.getJavaClassObject();
            return dynamicClassLoader.defineClass(mainClass);
        }


        private String detailErrorDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics, StringJavaFileObject file) {
            StringBuilder result = new StringBuilder("【");
            for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                appendErrorDetail(diagnostic, result, file);
            }
            result.append("】");
            return result.toString();
        }

        private void appendErrorDetail(Diagnostic<?> diagnostic, StringBuilder sb, StringJavaFileObject file) {
            if (diagnostic.getKind() != Diagnostic.Kind.ERROR) {
                return;
            }
            sb.append("the").append(diagnostic.getLineNumber()).append("line")
                    .append("(").append(file.getLineCode(diagnostic.getLineNumber()).trim()).append(")")
                    .append("occurs error:").append(diagnostic.getMessage(null))
                    .append(";");
        }


        public String getEncoding() {
            return encoding;
        }

        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }

        public String getClasspath() {
            return classpath;
        }

        public ClassLoader getParentClassLoader() {
            return parentClassLoader;
        }

        public JavaCompiler getCompiler() {
            return compiler;
        }

        /**
         * @Description
         * @Author shaopeng
         * @Date 2021/11/17
         * @Version 1.0
         */
        public static class StringJavaFileObject extends SimpleJavaFileObject {

            private String code;


            public StringJavaFileObject(String className, String code) {
                super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
                this.code = code;
            }


            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }

            /**
             * get code line
             */
            public String getLineCode(long line) {
                LineNumberReader reader = new LineNumberReader(new StringReader(code));
                int num = 0;
                String codeLine = null;
                try {
                    while ((codeLine = reader.readLine()) != null) {
                        num++;
                        if (num == line) {
                            break;
                        }
                    }
                } catch (Throwable ignored) {

                } finally {
                    try {
                        reader.close();
                    } catch (Exception e) {
                        // skip
                    }
                }
                return codeLine == null ? "" : codeLine;
            }
        }

        /**
         * @Description mery manage
         * @Author shaopeng
         * @Date 2021/11/17
         * @Version 1.0
         */
        public static class MemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
            private JavaClassObject mainObject;

            private List<JavaClassObject> innerObjects = new ArrayList<>();

            protected MemoryFileManager(JavaFileManager fileManager) {
                super(fileManager);
            }

            public byte[] getJavaClass() {
                return mainObject.getBytes();
            }

            public JavaClassObject getJavaClassObject() {
                return mainObject;
            }

            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
                mainObject = new JavaClassObject(className, kind);
                innerObjects.add(mainObject);
                return mainObject;
            }

            public List<JavaClassObject> getInnerClassJavaClassObject() {
                if (this.innerObjects != null && this.innerObjects.size() > 0) {
                    int size = this.innerObjects.size();
                    if (size == 1) {
                        return Collections.emptyList();
                    }
                    return this.innerObjects.subList(0, size - 1);
                }
                return Collections.emptyList();
            }

            @Override
            public void close() throws IOException {
                if (null != mainObject) {
                    mainObject.delete();
                }
                super.close();
            }

        }

        /**
         * @Description diy loader
         * @Author shaopeng
         * @Date 2021/11/17
         * @Version 1.0
         */
        public static class DynamicClassLoader extends URLClassLoader {

            public DynamicClassLoader(ClassLoader parent) {
                super(new URL[0], parent);
            }


            public Class<?> defineClass(JavaClassObject javaClassObject) {
                String name = getClassFullName(javaClassObject);
                byte[] classData = javaClassObject.getBytes();
                return super.defineClass(name, classData, 0, classData.length);
            }


            private static String getClassFullName(JavaClassObject javaClassObject) {
                String name = javaClassObject.getName();
                name = name.substring(1, name.length() - 6);
                name = name.replace("/", ".");
                return name;
            }

        }

        /**
         * @Description
         * @Author shaopeng
         * @Date 2021/11/17
         * @Version 1.0
         */
        public static class JavaClassObject extends SimpleJavaFileObject {

            protected final ByteArrayOutputStream bos = new ByteArrayOutputStream();


            public JavaClassObject(String name, Kind kind) {
                super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
            }

            public byte[] getBytes() {
                return bos.toByteArray();
            }

            @Override
            public OutputStream openOutputStream() throws IOException {
                return bos;
            }
        }

        /**
         * @Description compileEx
         * @Author shaopeng
         * @Date 2021/11/30
         * @Version 1.0
         */
        public static class CompileException extends RuntimeException {
            private static final long serialVersionUID = 7544121017136444232L;

            public CompileException(String message) {
                super(message);
            }
        }
    }

}
