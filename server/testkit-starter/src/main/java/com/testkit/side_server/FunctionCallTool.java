package com.testkit.side_server;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.*;

public class FunctionCallTool {

    @Autowired
    private ApplicationContext applicationContext;

    public Object process(Map<String, String> params) throws Exception {
        // 解析类名、方法名、方法参数类型
        String typeClassStr = params.get("typeClass");
        String beanName = params.get("beanName");
        String methodName = params.get("methodName");
        String methodArgTypesStr = params.get("argTypes");
        String methodArgsStr = params.get("args");
        boolean original = Boolean.parseBoolean(params.get("original"));

        // 获取类和方法
        Class<?> typeClass = null;
        try {
            typeClass = Class.forName(typeClassStr);
        } catch (ClassNotFoundException e) {
            throw new TestkitException("can not find class: " + typeClassStr + ", please check");
        }
        ReflexBox reflexBox = ReflexUtils.parse(typeClass, methodName, methodArgTypesStr, methodArgsStr);
        Object bean = null;
        if (beanName == null || beanName.trim().isEmpty()) {
            Map<String, ?> beansOfType = applicationContext.getBeansOfType(typeClass);
            if (beansOfType.isEmpty()) {
                throw new TestkitException("can not find " + typeClass + " in this spring");
            } else if (beansOfType.size() > 1) {
                throw new TestkitException("no union bean of type " + typeClass + " in this spring");
            }
            bean = beansOfType.values().iterator().next();
        } else {
            try {
                bean = applicationContext.getBean(beanName, typeClass);
            } catch (BeansException e) {
                throw new TestkitException("can not find " + typeClass + " in this spring," + e.getMessage());
            }
        }
        if (original && ReflexUtils.isAopProxy(bean)) {
            try {
                bean = ReflexUtils.getTargetObject(bean);
            } catch (Throwable e) {
                throw new TestkitException("find original obj fail，" + e);
            }
        }
        return reflexBox.execute(bean);
    }


}