package com.fling.tools;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fling.util.JsonUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class ToolHelper {

    public static boolean isPrimitiveWrapper(PsiType psiType) {
        if (psiType == null) {
            return false;
        }

        String canonicalText = psiType.getCanonicalText();

        return "java.lang.Boolean".equals(canonicalText) ||
                "java.lang.Byte".equals(canonicalText) ||
                "java.lang.Character".equals(canonicalText) ||
                "java.lang.Double".equals(canonicalText) ||
                "java.lang.Float".equals(canonicalText) ||
                "java.lang.Integer".equals(canonicalText) ||
                "java.lang.Long".equals(canonicalText) ||
                "java.lang.Short".equals(canonicalText);
    }


    public static String buildMethodKey(PsiMethod method) {
        if (method == null || !method.isValid()) {
            return null;
        }
        // 获取类名
        PsiClass containingClass = method.getContainingClass();
        String className = containingClass != null ? containingClass.getName() : "UnknownClass";

        // 获取方法名
        String methodName = method.getName();

        // 获取参数列表
        StringBuilder parameters = new StringBuilder();
        PsiParameterList parameterList = method.getParameterList();
        for (PsiParameter parameter : parameterList.getParameters()) {
            if (parameters.length() > 0) {
                parameters.append(", ");
            }
            parameters.append(parameter.getType().getPresentableText());
        }

        // 构建完整的方法签名
        return className + "#" + methodName + "(" + parameters.toString() + ")";
    }

    public static String buildXmlTagKey(XmlTag xmlTag) {
        if (xmlTag == null || !xmlTag.isValid()) {
            return null;
        }

        // 获取标签名
        String tagName = xmlTag.getContainingFile().getName();

        // 构建标识符，假设我们使用标签名称和某个关键属性进行组合
        StringBuilder keyBuilder = new StringBuilder(tagName);

        // 假设我们关注某个特定属性，比如 "id"，这个可以根据具体业务规则定制
        String idAttribute = xmlTag.getAttributeValue("id");
        if (idAttribute != null) {
            keyBuilder.append("#").append(idAttribute);
        }

        // 当然，你可以根据需要加入更多的信息，比如标签的命名空间或其他属性
        // String namespace = xmlTag.getNamespace();
        // keyBuilder.append("(namespace: ").append(namespace).append(")");

        return keyBuilder.toString();
    }


    public static JSONArray adapterParams(PsiMethod psiMethod, JSONObject args) {
        if (psiMethod == null || args == null) {
            return new JSONArray();
        }

        JSONArray jsonArray = new JSONArray();
        for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
            String paramName = parameter.getName();
            // 如果参数不在args中，填充null
            jsonArray.add(args.getOrDefault(paramName, null));
        }
        return jsonArray;
    }

    public static void initParamsTextField(EditorTextField editorTextField, MethodAction method) {
        if (method.getArgs() != null) {
            editorTextField.setText(method.getArgs());
            return;
        }
        editorTextField.setText("init params ...");
        // 在这里执行耗时操作
        JSONObject initParams = doInitParams(method.getMethod());
        String jsonString = JsonUtil.formatObj(initParams);
        editorTextField.setText(jsonString);
        method.setArgs(jsonString);
    }

    private static JSONObject doInitParams(PsiMethod method) {
        JSONObject initParams = new JSONObject();
        PsiParameter[] parameters = method.getParameterList().getParameters();

        for (PsiParameter parameter : parameters) {
            PsiType type = parameter.getType();
            String name = parameter.getName();

            if (type.equalsToText("boolean")) {
                initParams.put(name, false);
            } else if (type.equalsToText("char")) {
                initParams.put(name, '\0');
            } else if (type.equalsToText("byte")) {
                initParams.put(name, (byte) 0);
            } else if (type.equalsToText("short")) {
                initParams.put(name, (short) 0);
            } else if (type.equalsToText("int")) {
                initParams.put(name, 0);
            } else if (type.equalsToText("long")) {
                initParams.put(name, 0L);
            } else if (type.equalsToText("float")) {
                initParams.put(name, 0.0f);
            } else if (type.equalsToText("double")) {
                initParams.put(name, 0.0);
            } else if (isPrimitiveWrapper(type)) {
                initParams.put(name, null);
            } else if (type.equalsToText("java.lang.String")) {
                initParams.put(name, null);
            } else if (type.equalsToText("java.util.Date")) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                initParams.put(name, sdf.format(new Date()));
            } else if (isEnumType(type)) {
                initParams.put(name, getFirstEnumConstant(type));
            } else if (isCollectionType(type)) {
                initParams.put(name, initializeCollection(type));
            } else if (isMapType(type)) {
                initParams.put(name, initializeMap(type));
            } else if (type instanceof PsiArrayType) {
                initParams.put(name, initializeArray(type));
            } else {
                // 为对象类型初始化一个带有可设置字段的 HashMap
                initParams.put(name, initializeObjectFields(type));
            }
        }

        return initParams;
    }

    private static boolean isEnumType(PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType) type).resolve();
            return psiClass != null && psiClass.isEnum();
        }
        return false;
    }

    private static Object getFirstEnumConstant(PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType) type).resolve();
            if (psiClass != null && psiClass.isEnum()) {
                PsiField[] fields = psiClass.getFields();
                for (PsiField field : fields) {
                    if (field instanceof PsiEnumConstant) {
                        return field.getName(); // 返回第一个枚举常量名称
                    }
                }
            }
        }
        return null; // 如果没有枚举常量，可返回默认值或 null
    }

    private static boolean isCollectionType(PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            return InheritanceUtil.isInheritor(classType, "java.util.Collection");
        }
        return false;
    }

    private static boolean isMapType(PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            return InheritanceUtil.isInheritor(classType, "java.util.Map");
        }
        return false;
    }

    private static Object initializeCollection(PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            PsiType[] parameters = classType.getParameters();

            // 默认空集合
            ArrayList<Object> collectionInit = new ArrayList<>();

            if (parameters.length == 1) {
                PsiType elementType = parameters[0];
                if (elementType.equalsToText("java.lang.String")) {
                    collectionInit.add("");
                } else if (elementType.equalsToText("java.lang.Integer")) {
                    collectionInit.add(0);
                } else if (elementType.equalsToText("java.lang.Boolean")) {
                    collectionInit.add(false);
                } else if (elementType.equalsToText("java.util.Date")) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    collectionInit.add(sdf.format(new Date()));
                } else if (isEnumType(elementType)) {
                    collectionInit.add(getFirstEnumConstant(elementType));
                } else if (elementType instanceof PsiClassType) {
                    // 对于对象类型的元素，初始化一个空对象结构
                    collectionInit.add(initializeObjectFields(elementType));
                }
            }

            return collectionInit;
        }

        // 没有泛型参数时返回空列表
        return new ArrayList<>();
    }

    private static Object initializeMap(PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            PsiType[] parameters = classType.getParameters();

            HashMap<Object, Object> mapInit = new HashMap<>();

            if (parameters.length == 2) { // Map should have two type parameters: key and value
                PsiType keyType = parameters[0];
                PsiType valueType = parameters[1];

                Object defaultKey = getDefaultValueForType(keyType);
                Object defaultValue = getDefaultValueForType(valueType);

                mapInit.put(defaultKey, defaultValue);
            }

            return mapInit;
        }

        return new HashMap<>();
    }

    private static Object initializeArray(PsiType type) {
        if (type instanceof PsiArrayType) {
            PsiArrayType arrayType = (PsiArrayType) type;
            PsiType componentType = arrayType.getComponentType();

            ArrayList<Object> arrayInit = new ArrayList<>();

            Object defaultValue = getDefaultValueForType(componentType);
            arrayInit.add(defaultValue);

            return arrayInit;
        }

        return new ArrayList<>();
    }

    private static Object getDefaultValueForType(PsiType type) {
        if (type.equalsToText("java.lang.String") || isPrimitiveWrapper(type)) {
            return null;
        } else if (type.equalsToText("java.util.Date")) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(new Date());
        } else if (isEnumType(type)) {
            return getFirstEnumConstant(type);
        } else if (isCollectionType(type)) {
            return initializeCollection(type);
        } else if (isMapType(type)) {
            return initializeMap(type);
        } else if (type instanceof PsiArrayType) {
            return initializeArray(type);
        } else {
            return initializeObjectFields(type);
        }
    }

    private static Object initializeObjectFields(PsiType type) {
        if (!(type instanceof PsiClassType)) {
            return null;
        }
        if (isPrimitiveWrapper(type)) {
            return null;
        }

        PsiClassType classType = (PsiClassType) type;
        PsiClass psiClass = classType.resolve();
//        如果是 com.baomidou.mybatisplus.extension.plugins.pagination.Page 就给个 size:10,current=1的jsonobject

        if (psiClass == null) {
            return new HashMap<>();
        }

        // 检查是否为 Page 类型
        if ("com.baomidou.mybatisplus.extension.plugins.pagination.Page".equals(psiClass.getQualifiedName())) {
            JSONObject pageObject = new JSONObject();
            pageObject.put("size", 10);
            pageObject.put("current", 1);
            return pageObject;
        }

        HashMap<String, Object> fieldValues = new HashMap<>();

        for (PsiMethod method : psiClass.getAllMethods()) {
            String methodName = method.getName();

            if ((methodName.startsWith("set") && method.getParameterList().getParametersCount() == 1) ||
                    (methodName.startsWith("is") && method.getParameterList().getParametersCount() == 1 && isBooleanType(method.getParameterList().getParameters()[0].getType()))) {

                PsiParameter setterParameter = method.getParameterList().getParameters()[0];
                PsiType fieldType = setterParameter.getType();

                // 根据方法前缀提取字段名
                String fieldName;
                if (methodName.startsWith("set")) {
                    if (methodName.length() == 3) {
                        continue;
                    }
                    fieldName = methodName.substring(3);
                } else { // is
                    if (methodName.length() == 2) {
                        continue;
                    }
                    fieldName = methodName.substring(2);
                }

                // 将字段名首字母小写
                fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);

                // 初始化字段值
                if (fieldType.equalsToText("java.lang.String") || isEnumType(fieldType)) {
                    fieldValues.put(fieldName, null);
                } else if (fieldType.equalsToText("boolean")) {
                    fieldValues.put(fieldName, false);
                } else if (fieldType.equalsToText("char")) {
                    fieldValues.put(fieldName, '\0');
                } else if (fieldType.equalsToText("byte")) {
                    fieldValues.put(fieldName, (byte) 0);
                } else if (fieldType.equalsToText("short")) {
                    fieldValues.put(fieldName, (short) 0);
                } else if (fieldType.equalsToText("int")) {
                    fieldValues.put(fieldName, 0);
                } else if (fieldType.equalsToText("long")) {
                    fieldValues.put(fieldName, 0L);
                } else if (fieldType.equalsToText("float")) {
                    fieldValues.put(fieldName, 0.0f);
                } else if (fieldType.equalsToText("double")) {
                    fieldValues.put(fieldName, 0.0);
                } else if (fieldType.equalsToText("java.util.Date")) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    fieldValues.put(fieldName, sdf.format(new Date()));
                } else if (isEnumType(fieldType)) {
                    fieldValues.put(fieldName, getFirstEnumConstant(fieldType));
                } else if (isCollectionType(fieldType)) {
                    fieldValues.put(fieldName, initializeCollection(fieldType));
                } else if (isMapType(fieldType)) {
                    fieldValues.put(fieldName, new HashMap<>());
                } else {
                    // 如果是对象类型，递归调用初始化
                    fieldValues.put(fieldName, initializeObjectFields(fieldType));
                }
            }
        }

        return fieldValues;
    }

    private static boolean isBooleanType(PsiType type) {
        return type.equalsToText("java.lang.Boolean") || type.equalsToText("boolean");
    }


    public static String getBeanNameFromClass(PsiClass psiClass) {
        PsiModifierList modifierList = psiClass.getModifierList();
        String beanName = null;

        if (modifierList != null) {
            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                String qualifiedName = annotation.getQualifiedName();
                if (qualifiedName != null &&
                        (qualifiedName.equals("org.springframework.stereotype.Component") ||
                                qualifiedName.equals("org.springframework.stereotype.Service") ||
                                qualifiedName.equals("org.springframework.stereotype.Repository") ||
                                qualifiedName.equals("org.springframework.context.annotation.Configuration") ||
                                qualifiedName.equals("org.springframework.stereotype.Controller") ||
                                qualifiedName.equals("org.springframework.web.bind.annotation.RestController") ||
                                qualifiedName.equals("org.apache.ibatis.annotations.Mapper"))) {

                    if (annotation.findAttributeValue("value") != null) {
                        beanName = annotation.findAttributeValue("value").getText().replace("\"", "");
                    }
                }
            }
        }

        if (beanName == null || beanName.isBlank()) {
            String className = psiClass.getName();
            if (className != null && !className.isEmpty()) {
                beanName = Character.toLowerCase(className.charAt(0)) + className.substring(1);
            }
        }

        return beanName;
    }

    public static String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        while (throwable instanceof InvocationTargetException) {
            throwable = ((InvocationTargetException) throwable).getTargetException();
        }
        if (throwable.getCause() != null && throwable.getCause() != throwable) {
            throwable = throwable.getCause();
        }
        final StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw, true));
        return sw.toString();
    }

    public static boolean isSpringBean(@NotNull PsiClass psiClass) {
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

    public static class MethodAction {

        private final PsiMethod method;

        private String args;

        public MethodAction(PsiMethod method) {
            this.method = method;
        }

        public String getArgs() {
            return args;
        }

        public void setArgs(String args) {
            this.args = args;
        }


        @Override
        public String toString() {
            if (method == null) {
                return "unknown";
            }
            String s = buildMethodKey(method);
            return s == null ? "invalid" : s;
        }

        public PsiMethod getMethod() {
            return method;
        }
    }


    public static class XmlTagAction {

        private final XmlTag xmlTag;

        private String args;

        public XmlTagAction(XmlTag xmlTag) {
            this.xmlTag = xmlTag;
        }


        @Override
        public String toString() {
            if (xmlTag == null) {
                return "unknown";
            }
            String s = buildXmlTagKey(xmlTag);
            return s == null ? "invalid" : s;
        }


        public String getArgs() {
            return args;
        }

        public void setArgs(String args) {
            this.args = args;
        }

        public XmlTag getXmlTag() {
            return xmlTag;
        }
    }
}
