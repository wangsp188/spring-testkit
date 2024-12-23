package com.fling;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * jdk dynamic compiler
 * Thread safety
 *
 * @Author shaopeng
 * @Date 2021/11/30
 * @Version 1.0
 * @see JdkDynamicCompiler#compile
 */
public class JdkDynamicCompiler {


    private static JdkDynamicCompiler compiler = new JdkDynamicCompiler();


    public static Class compileCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return compiler.compile(code);
    }


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
    private final JavaCompiler javaCompiler;

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
        javaCompiler = ToolProvider.getSystemJavaCompiler();
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
        if (null == javaCompiler) {
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
        StandardJavaFileManager standardJavaFileManager = javaCompiler.getStandardFileManager(diagnostics, null, null);
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
            JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, diagnostics, options, null, files);
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

    public JavaCompiler getJavaCompiler() {
        return javaCompiler;
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
