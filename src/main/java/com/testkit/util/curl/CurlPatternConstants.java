package com.testkit.util.curl;

import java.util.regex.Pattern;

public interface CurlPatternConstants {
 
    /**
     * CURL基本结构校验
     */
    Pattern CURL_BASIC_STRUCTURE_PATTERN = Pattern.compile("^curl (\\S+)");
 
    /**
     * URL路径匹配
     */
    Pattern URL_PATH_PATTERN =
            Pattern.compile("(?:\\s|^)(?:'|\")?(https?://[^?\\s'\"]*)(?:\\?[^\\s'\"]*)?(?:'|\")?(?:\\s|$)");
 
    /**
     * 请求参数列表匹配
     */
    Pattern URL_PARAMS_PATTERN = Pattern.compile("(?:\\s|^)(?:'|\")?(https?://[^\\s'\"]+)(?:'|\")?(?:\\s|$)");
 
    /**
     * HTTP请求方法匹配
     */
    //Pattern HTTP_METHOD_PATTERN = Pattern.compile("(?:-X|--request)\\s+(\\S+)");
    Pattern HTTP_METHOD_PATTERN = Pattern.compile("curl\\s+[^\\s]*\\s+(?:-X|--request)\\s+'?(GET|POST)'?");
 
    /**
     * 默认HTTP请求方法匹配
     */
    Pattern DEFAULT_HTTP_METHOD_PATTERN = Pattern.compile("(-d|--data|--data-binary|--data-raw|--data-urlencode|-F|--form)");
 
    /**
     * 请求头匹配
     */
    Pattern CURL_HEADERS_PATTERN = Pattern.compile("(?:-H|--header)\\s+'(.*?:.*?)'");
 
    /**
     * -d/--data 请求体匹配
     */
    Pattern DEFAULT_HTTP_BODY_PATTERN = Pattern.compile("(?:--data|-d)\\s+(?:'([^']*)'|\"([^\"]*)\"|(\\S+))", Pattern.DOTALL);
    Pattern DEFAULT_HTTP_BODY_PATTERN_KV = Pattern.compile("^([^=&]+=[^=&]+)(?:&[^=&]+=[^=&]+)*$", Pattern.DOTALL);
 
    /**
     * --data-raw 请求体匹配
     */
    Pattern HTTP_ROW_BODY_PATTERN = Pattern.compile("--data-raw\\s+(?:'([^']*)'|\"([^\"]*)\"|(\\S+))", Pattern.DOTALL);
 
    /**
     * --form 请求体匹配
     */
    Pattern HTTP_FROM_BODY_PATTERN = Pattern.compile("--form\\s+'(.*?)'|-F\\s+'(.*?)'");
 
 
    /**
     * --data-urlencode 请求体匹配
     */
    Pattern HTTP_URLENCODE_BODY_PATTERN = Pattern.compile("--data-urlencode\\s+(?:'([^']*)'|\"([^\"]*)\"|(\\S+))", Pattern.DOTALL);
 
}