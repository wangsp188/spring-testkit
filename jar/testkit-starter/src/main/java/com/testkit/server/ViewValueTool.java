package com.testkit.server;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ViewValueTool implements TestkitTool {

    private static final List<Class> springTypes = new ArrayList<>();

    static {
        springTypes.add(Autowired.class);
        springTypes.add(Qualifier.class);

        try {
            springTypes.add(Class.forName("javax.annotation.Resource"));
        } catch (ClassNotFoundException e) {
            try {
                springTypes.add(Class.forName("jakarta.annotation.Resource"));
            } catch (ClassNotFoundException ex) {
            }
        }
    }

    private ApplicationContext app;

    public ViewValueTool(ApplicationContext app) {
        this.app = app;
    }

    @Override
    public String method() {
        return "view-value";
    }

    @Override
    public PrepareRet prepare(Map<String, String> params) throws Exception {
        String typeClassStr = params.get("typeClass");
        String beanName = params.get("beanName");
        String fieldName = params.get("fieldName");

        // 反射获取class  
        Class<?> typeClass = null;
        try {
            typeClass = Class.forName(typeClassStr);
        } catch (ClassNotFoundException e) {
            throw new TestkitException("can not find class: " + typeClassStr + ", please check");
        }

        // 获取字段  
        Field field = null;
        // 判断该字段是否是 Spring 自动注入的 Bean
        try {
            field = typeClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            //判断该字段是否是spring自动注入的bean
        } catch (Throwable e) {
            throw new TestkitException("can not find field: " + fieldName + ", please check");
        }

        // 检查字段是否为static
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            // 如果是static字段，直接从类获取
            Field finalField1 = field;
            return new PrepareRet() {
                @Override
                public String confirm() {
                    return null;
                }

                @Override
                public Object execute() throws Exception {
                    return finalField1.get(null);
                }
            };
        }


        Field finalField = field;
        boolean isSpringInjected = springTypes.stream().anyMatch(new Predicate<Class>() {
            @Override
            public boolean test(Class aClass) {
                return finalField.isAnnotationPresent(aClass);
            }
        });

        // 如果不是static字段，从Spring context中获取bean
        Map<String, Object> beansOfType = null;
        if (beanName == null || beanName.trim().isEmpty()) {
            beansOfType = (Map<String, Object>) app.getBeansOfType(typeClass);
            if (beansOfType.isEmpty()) {
                throw new TestkitException("can not find " + typeClass + " in this spring");
            }
        } else {
            try {
                beansOfType = new HashMap<>();
                Object bean = app.getBean(beanName, typeClass);
                beansOfType.put(beanName, bean);
            } catch (BeansException e) {
                throw new TestkitException("can not find " + typeClass + " in this spring," + e.getMessage());
            }
        }

        //超过一个，自动组成map告诉他
        HashMap<String, Object> oriMap = new HashMap<>();
        for (Map.Entry<String, ?> stringEntry : beansOfType.entrySet()) {
            Object targetObject = ReflexUtils.getTargetObject(stringEntry.getValue());
            if (!typeClass.isAssignableFrom(targetObject.getClass())) {
                throw new TestkitException("bean:" + stringEntry.getKey() + " is not type of " + typeClassStr);
            }
            oriMap.put(stringEntry.getKey(), targetObject);
        }

        return new PrepareRet() {
            @Override
            public String confirm() throws Exception {
                return null;
            }

            @Override
            public Object execute() throws Exception {
                if (oriMap.size() == 1) {
                    return adapter(finalField.get(oriMap.values().iterator().next()), isSpringInjected);
                }
                HashMap<Object, Object> map = new HashMap<>();
                for (Map.Entry<String, Object> stringEntry : oriMap.entrySet()) {
                    map.put(stringEntry.getKey(), adapter(finalField.get(stringEntry.getValue()), isSpringInjected));
                }
                return map;
            }
        };
    }


    private Object adapter(Object obj, boolean isSpringInjected) {
        if (obj == null) {
            return null;
        }
        if (!isSpringInjected) {
            return obj;
        }
        if (obj instanceof Collection) {
            return ((Collection<?>) obj)
                    .stream()
                    .filter(Objects::nonNull)
                    .map(new Function<Object, Object>() {
                        @Override
                        public Object apply(Object o) {
                            return o.toString();
                        }
                    }).collect(Collectors.toList());
        } else if (obj instanceof Map) {
            HashMap<Object, Object> map = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                map.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            return map;
        }
        return obj.toString();
    }
}