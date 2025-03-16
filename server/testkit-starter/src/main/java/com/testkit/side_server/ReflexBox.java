package com.testkit.side_server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class ReflexBox {

    private Method method;

    private Object[] args;

    public ReflexBox(Method method, Object[] args) {
        this.method = method;
        this.args = args;
    }

    public Object execute(Object typeInstance) throws InvocationTargetException, IllegalAccessException {
        return method.invoke(typeInstance, args);
    }


    public Method getMethod() {
        return method;
    }

    public Object[] getArgs() {
        return args;
    }
}
