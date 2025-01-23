package com.testkit.util.curl;

import org.apache.commons.lang3.StringUtils;

public class CurlParserUtil {
    /**
     * 该方法是用来解析CURL的入口。
     *
     * @param curl 输入的CURL文本字符串
     * @return 返回解析后生成的CURL实体对象
     */
    public static CurlEntity parse(String curl) {
        if (StringUtils.isBlank(curl)) {
            return null;
        }
        curl = curl.trim();
        if (curl.startsWith("http")) {
//            localhost://
            curl = "curl '" + curl + "'";
        }else if(curl.startsWith("/")) {
            curl = "curl 'http://unknowndomain" + curl + "'";
        }


        CurlEntity entity = new CurlEntity();
        ICurlHandler<CurlEntity, String> handlerChain = CurlHandlerChain.init();

        // 如需扩展其他解析器，继续往链表中add即可
        handlerChain.next(new UrlPathHandler())
                .next(new UrlParamsHandler())
                .next(new HttpMethodHandler())
                .next(new HeaderHandler())
                .next(new HttpBodyHandler());

        handlerChain.handle(entity, curl);
        return entity;
    }
}