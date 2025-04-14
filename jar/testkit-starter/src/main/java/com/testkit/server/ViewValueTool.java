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
        Field finalField = field;
        boolean isSpringInjected = springTypes.stream().anyMatch(new Predicate<Class>() {
            @Override
            public boolean test(Class aClass) {
                return finalField.isAnnotationPresent(aClass);
            }
        });

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
                    return adapter(finalField1.get(null), isSpringInjected);
                }
            };
        }
        // 如果不是static字段，从Spring context中获取bean
        Object bean = null;
        if (beanName == null || beanName.trim().isEmpty()) {
            Map<String, ?> beansOfType = app.getBeansOfType(typeClass);
            if (beansOfType.isEmpty()) {
                throw new TestkitException("can not find " + typeClass + " in this spring");
            } else if (beansOfType.size() > 1) {
                throw new TestkitException("no union bean of type " + typeClass + " in this spring");
            }
            bean = beansOfType.values().iterator().next();
        } else {
            try {
                bean = app.getBean(beanName, typeClass);
            } catch (BeansException e) {
                throw new TestkitException("can not find " + typeClass + " in this spring," + e.getMessage());
            }
        }

        // 获取原始对象
        Object targetObject = ReflexUtils.getTargetObject(bean);
        if (!typeClass.isAssignableFrom(targetObject.getClass())) {
            throw new TestkitException("bean is not type of " + typeClassStr);
        }
        Field finalField2 = field;
        return new PrepareRet() {
            @Override
            public String confirm() {
                return null;
            }

            @Override
            public Object execute() throws Exception {
                return adapter(finalField2.get(targetObject), isSpringInjected);
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