package com.fling.util.curl;

public interface ICurlHandler<R, S> {
 
    ICurlHandler<CurlEntity, String> next(ICurlHandler<CurlEntity, String> handler);
 
    void handle(CurlEntity entity, String curl);
}