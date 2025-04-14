package com.testkit.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.*;

public class FlexibleTestTool implements TestkitTool {

    private ApplicationContext app;

    public FlexibleTestTool(ApplicationContext app) {
        this.app = app;
    }

    @Override
    public String method() {
        return "flexible-test";
    }

    @Override
    public PrepareRet prepare(Map<String, String> params) throws JsonProcessingException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException {
        String code = params.remove("code");
        String methodName = params.get("methodName");
        String methodArgTypesStr = params.get("argTypes");
        String methodArgsStr = params.get("args");
        if (code == null || code.isEmpty()) {
            throw new TestkitException("code is null");
        }
        Class<?> typeClass = null;
        try {
            typeClass = ReflexUtils.compile(code);
        } catch (Throwable e) {
            throw new TestkitException("code compile error, please check, " + e);
        }
        //        instance and inject
        Object instance = null;
        try {
            Constructor<?> declaredConstructor = typeClass.getDeclaredConstructor();
            declaredConstructor.setAccessible(true);
            instance = declaredConstructor.newInstance();
        } catch (Throwable e) {
            throw new TestkitException(typeClass.getName() + "instance error, class must has none params public constructor," + e);
        }
        try {
            ReflexUtils.autowireBean(app.getAutowireCapableBeanFactory(), instance);
        } catch (Throwable e) {
            throw new TestkitException("autowireBean error, " + e);
        }

        ReflexBox reflexBox = ReflexUtils.parse(typeClass, methodName, methodArgTypesStr, methodArgsStr);
        Object finalInstance = instance;
        return new PrepareRet() {
            @Override
            public String confirm() {
                return MessageFormat.format(TestkitTool.RED+"Can you confirm execute flexible-test?\n"+TestkitTool.RESET+TestkitTool.YELLOW+"Method: {0}\n"+TestkitTool.RESET+"{1}"+TestkitTool.GREEN+"\nCode:\n"+TestkitTool.RESET+"{2}",reflexBox.buildMethodStr(),reflexBox.buildArgStr(),code);
            }

            @Override
            public Object execute() throws Exception{
                return reflexBox.execute(finalInstance);
            }
        };
    }

}
