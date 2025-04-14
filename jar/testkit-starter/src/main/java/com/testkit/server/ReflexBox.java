package com.testkit.server;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

class ReflexBox {

    private Method method;

    private Object[] args;

    public ReflexBox(Method method, Object[] args) {
        this.method = method;
        this.args = args;
    }


    public String buildMethodStr(){
        return method == null ? "" : method.toGenericString();
    }

    public String buildArgStr(){
        if (args == null) {
            return TestkitTool.RED + "Args:null" + TestkitTool.RESET;
        }
        if (args.length == 0) {
            return TestkitTool.RED + "Args:empty" + TestkitTool.RESET;
        }

        StringBuilder builder = new StringBuilder(TestkitTool.RED+"Args"+TestkitTool.RESET);
        for (int i = 0; i < args.length; i++) {
            builder.append("\n"+TestkitTool.RED+"arg").append(i).append(TestkitTool.RESET).append(":");
            Object arg = args[i];
            if (arg==null) {
                builder.append("null");
            }else if(arg instanceof String || ReflexUtils.isPrimitiveOrWrapper(arg)){
                builder.append(arg);
            }else if(arg instanceof Date){
                builder.append(ReflexUtils.dateFormat.format(arg));
            }else {
                try {
                    builder.append(ReflexUtils.SIMPLE_MAPPER.writeValueAsString(arg));
                } catch (Throwable e) {
                    Object reObj = null;
                    if (arg.getClass().isArray()) {
                        // 如果是array
                        Object[] array = (Object[]) arg;
                        for (int y = 0; y < array.length; y++) {
                            array[y] = String.valueOf(array[y]);
                        }
                        reObj = array;
                    } else if (arg instanceof Collection) {
                        // 如果是collection
                        Collection<?> collection = (Collection<?>) arg;
                        Collection<String> newCollection = new ArrayList<>();
                        for (Object element : collection) {
                            newCollection.add(String.valueOf(element));
                        }
                        reObj = newCollection;
                    } else if (arg instanceof Map) {
                        // 如果是map
                        Map<?, ?> map = (Map<?, ?>) arg;
                        Map<String, String> newMap = new HashMap<>();
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            newMap.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                        }
                        reObj = newMap;
                    } else {
                        // 其他情况
                        builder.append(arg);
                        continue;
                    }
                    try {
                        builder.append(ReflexUtils.SIMPLE_MAPPER.writeValueAsString(reObj));
                    } catch (JsonProcessingException ex) {
                        builder.append(arg);
                    }
                }
            }
        }
        return builder.toString();
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
