package com.testkit.server;

import java.util.Map;

public interface TestkitTool {

    String RESET = "\033[0m";
    String GREEN = "\033[1;32m";  // 亮绿色
    String YELLOW = "\033[1;33m"; // 亮黄色
    String RED = "\033[1;31m";    // 亮红色

    String method();
    PrepareRet prepare(Map<String, String> params) throws Exception;
}
