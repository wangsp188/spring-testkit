package com.testkit.server;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import java.text.MessageFormat;
import java.util.*;

public class FunctionCallTool implements TestkitTool {

    private ApplicationContext app;

    public FunctionCallTool(ApplicationContext app) {
        this.app = app;
    }

    @Override
    public String method() {
        return "function-call";
    }

    @Override
    public PrepareRet prepare(Map<String, String> params) throws Exception {
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
        if (original && ReflexUtils.isAopProxy(bean)) {
            try {
                bean = ReflexUtils.getTargetObject(bean);
            } catch (Throwable e) {
                throw new TestkitException("find original obj fail，" + e);
            }
        }
        String finalBeanName = beanName != null ? beanName : app.getBeanNamesForType(typeClass)[0];
        Object finalBean = bean;
        return new PrepareRet() {
            @Override
            public String confirm() {
                String beanStr = typeClassStr + "(" + finalBeanName+")"+(original ? (TestkitTool.RED+"[original]"+TestkitTool.RESET) : (TestkitTool.YELLOW+"[proxy]"+TestkitTool.RESET));
                return MessageFormat.format(TestkitTool.RED+"Can you confirm execute function-call?\n"+TestkitTool.RESET+TestkitTool.GREEN+"Bean: {0}\n"+TestkitTool.RESET+TestkitTool.YELLOW+"Method: {1}\n"+TestkitTool.RESET+"{2}",beanStr,reflexBox.buildMethodStr(),reflexBox.buildArgStr());
            }

            @Override
            public Object execute() throws Exception {
                return reflexBox.execute(finalBean);
            }
        };
    }


}