package com.testkit.util;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;

public class ExceptionUtil {

    public static String fetchStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }

        while (throwable instanceof InvocationTargetException) {
            throwable = ((InvocationTargetException) throwable).getTargetException();
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);

        // 处理整个异常链
        while (throwable != null) {
            // 打印异常信息
            pw.println(throwable);

            for (StackTraceElement element : throwable.getStackTrace()) {
                pw.println("\tat " + element);
            }

            // 获取下一个 cause
            throwable = throwable.getCause();
            if (throwable != null) {
                pw.println("Caused by: ");
            }
        }

        return sw.toString();
    }
}
