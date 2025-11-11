package com.testkit.server;

import org.springframework.beans.BeansException;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.*;
import org.springframework.context.ApplicationContext;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SpringCacheTool implements TestkitTool {


    private ApplicationContext app;

    private CacheAspectSupport cacheAspectSupport;

    private CacheOperationSource cacheOperationSource;

    public SpringCacheTool(ApplicationContext app) {
        this.app = app;
        try {
            this.cacheAspectSupport = app.getBean(CacheAspectSupport.class);
            this.cacheOperationSource = app.getBean(CacheOperationSource.class);
        } catch (Throwable ignore) {
        }
    }


    Method operationContextMethod;
    Method generateKeyMethod;
    Method getCachesMethod;
    Method redisCacheGenerateKeyMethod;
    Class<?> redisCacheCls;


    {
        try {
            Class<?> operationContextClass = Class.forName("org.springframework.cache.interceptor.CacheAspectSupport$CacheOperationContext");
//            Class<?> operationContextsClass = Class.forName("org.springframework.cache.interceptor.CacheAspectSupport$CacheOperationContexts");

            operationContextMethod = CacheAspectSupport.class.getDeclaredMethod("getOperationContext", CacheOperation.class, Method.class, Object[].class, Object.class, Class.class);
            operationContextMethod.setAccessible(true);
            generateKeyMethod = CacheAspectSupport.class.getDeclaredMethod("generateKey", operationContextClass, Object.class);
            generateKeyMethod.setAccessible(true);

            getCachesMethod = operationContextClass.getDeclaredMethod("getCaches");
            getCachesMethod.setAccessible(true);

            redisCacheCls = Class.forName("org.springframework.data.redis.cache.RedisCache");
            redisCacheGenerateKeyMethod = redisCacheCls.getDeclaredMethod("createCacheKey", Object.class);
            redisCacheGenerateKeyMethod.setAccessible(true);
        } catch (Throwable ignore) {
        }
    }

    @Override
    public String method() {
        return "spring-cache";
    }

    @Override
    public PrepareRet prepare(Map<String, String> params) throws Exception{
        if (cacheAspectSupport == null || cacheOperationSource == null || operationContextMethod == null || generateKeyMethod == null || getCachesMethod == null) {
            throw new TestkitException("The selected app not find spring-cache or System incompatibility, please contact the owner");
        }
        // 解析类名、方法名、方法参数类型
        String typeClassStr = params.get("typeClass");
        String beanName = params.get("beanName");
        String methodName = params.get("methodName");
        String methodArgTypesStr = params.get("argTypes");
        String methodArgsStr = params.get("args");
        String action = params.get("action");

        // 获取类和方法
        Class<?> typeClass = null;
        try {
            typeClass = Class.forName(typeClassStr);
        } catch (ClassNotFoundException e) {
            throw new TestkitException("can not find class: " + typeClassStr + ", please check");
        }
        if (!cacheOperationSource.isCandidateClass(typeClass)) {
            throw new TestkitException("typeClass is not Cache");
        }
        ReflexBox reflexBox = ReflexUtils.parse(typeClass, methodName, methodArgTypesStr, methodArgsStr);
        Object bean = ReflexUtils.getBean(app, beanName, typeClass);
        Class<?> finalTypeClass = typeClass;
        Object finalBean = bean;
        return new PrepareRet() {
            @Override
            public String confirm() throws Exception{
                if(!"delete_cache".equals(action)){
                    return null;
                }
                //构建key
                return MessageFormat.format(TestkitTool.RED+"Can you confirm to delete cache?\n"+TestkitTool.RESET+TestkitTool.YELLOW+"Keys: \n{0}",prepareDelKey(finalTypeClass, reflexBox.getMethod(), finalBean, reflexBox.getArgs()));
            }

            @Override
            public Object execute() throws Exception {
                return doAction(finalTypeClass, reflexBox.getMethod(), finalBean, reflexBox.getArgs(), action);
            }
        };
    }


    public List doAction(Class typeClass, Method method, Object obj, Object[] args, String action) throws Exception {
        Collection<CacheOperation> cacheOperations = cacheOperationSource.getCacheOperations(method, typeClass);
        if (cacheOperations == null || cacheOperations.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, List<KV>>> keyMap = new ArrayList<>();
        for (CacheOperation cacheOperation : cacheOperations) {
            Object cachectx = operationContextMethod.invoke(cacheAspectSupport, cacheOperation, method, args, obj, typeClass);
            Object key = generateKeyMethod.invoke(cacheAspectSupport, cachectx, null);
            if (key == null || (key instanceof Optional && !((Optional<?>) key).isPresent()) || String.valueOf(key).isEmpty()) {
                continue;
            }
            Collection<Cache> caches = (Collection<Cache>) getCachesMethod.invoke(cachectx);
            List<KV> keys = new ArrayList<>();
            if (cacheOperation instanceof CacheableOperation) {
                for (Cache cache : caches) {
                    if (!redisCacheCls.isAssignableFrom(cache.getClass())) {
                        throw new TestkitException("un support cacheType, only support RedisCache");
                    }
                    Object invoke = redisCacheGenerateKeyMethod.invoke(cache, key);
                    if (invoke == null) {
                        continue;
                    }
                    if ("build_cache_key".equals(action)) {
                        keys.add(KV.build(invoke.toString()));
                    } else if ("get_cache".equals(action)) {
                        Cache.ValueWrapper valueWrapper = cache.get(key);
                        keys.add(KV.build(invoke, valueWrapper == null ? null : valueWrapper.get()));
                    } else {
                        boolean evict = cache.evictIfPresent(key);
                        keys.add(KV.build(invoke, evict));
                    }
                }
            } else if (cacheOperation instanceof CacheEvictOperation || cacheOperation instanceof CachePutOperation) {
                for (Cache cache : caches) {
                    if (!redisCacheCls.isAssignableFrom(cache.getClass())) {
                        throw new TestkitException("un support cacheType, only support RedisCache");
                    }
                    if ("delete_cache".equals(action)) {
                        cache.evictIfPresent(key);
                    }
                    Object invoke = redisCacheGenerateKeyMethod.invoke(cache, key);
                    if (invoke == null) {
                        continue;
                    }
                    if ("build_cache_key".equals(action)) {
                        keys.add(KV.build(invoke.toString()));
                    } else if ("get_cache".equals(action)) {
                        Cache.ValueWrapper valueWrapper = cache.get(key);
                        keys.add(KV.build(invoke, valueWrapper == null ? null : valueWrapper.get()));
                    } else {
                        keys.add(KV.build(invoke));
                    }
                }
            } else {
                throw new TestkitException("un support cacheOperation");
            }
            HashMap<String, List<KV>> map = new HashMap<>();
            map.put(cacheOperation.getClass().getSimpleName(), keys);
            keyMap.add(map);
        }

        if ("build_cache_key".equals(action)) {
            return keyMap.stream().map(new Function<Map<String, List<KV>>, Object>() {
                @Override
                public Object apply(Map<String, List<KV>> stringListMap) {
                    //取出map的第一个key
                    String s = stringListMap.keySet().stream().findFirst().orElse(null);
                    if (s == null) {
                        return null;
                    }
                    List<KV> value = stringListMap.get(s);
                    if (value == null) {
                        return null;
                    }
                    HashMap<Object, Object> map = new HashMap<>();
                    map.put("operation", s);
                    map.put("buildKeys", value.stream().map(KV::getKey).collect(Collectors.toList()));
                    return map;
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
        } else if ("get_cache".equals(action)) {
            return keyMap.stream().map(new Function<Map<String, List<KV>>, Object>() {
                @Override
                public Object apply(Map<String, List<KV>> stringListMap) {
                    //取出map的第一个key
                    String s = stringListMap.keySet().stream().findFirst().orElse(null);
                    if (s == null) {
                        return null;
                    }
                    List<KV> value = stringListMap.get(s);
                    if (value == null) {
                        return null;
                    }

                    HashMap<Object, Object> kvs = new HashMap<>();
                    for (KV kv : value) {
                        if (kv == null || kv.getKey() == null) {
                            continue;
                        }
                        kvs.put(kv.getKey(), kv.getValue());
                    }


                    HashMap<Object, Object> map = new HashMap<>();
                    map.put("operation", s);
                    map.put("keyAndVals", kvs);
                    return map;
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
        }
        return keyMap.stream().map(new Function<Map<String, List<KV>>, Object>() {
            @Override
            public Object apply(Map<String, List<KV>> stringListMap) {
                //取出map的第一个key
                String s = stringListMap.keySet().stream().findFirst().orElse(null);
                if (s == null) {
                    return null;
                }
                List<KV> value = stringListMap.get(s);
                if (value == null) {
                    return null;
                }
                HashMap<Object, Object> map = new HashMap<>();
                map.put("operation", s);
                map.put("deleteKeys", value.stream().map(KV::getKey).collect(Collectors.toList()));
                return map;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }


    public List prepareDelKey(Class typeClass, Method method, Object obj, Object[] args) throws Exception {
        Collection<CacheOperation> cacheOperations = cacheOperationSource.getCacheOperations(method, typeClass);
        if (cacheOperations == null || cacheOperations.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, List>> keyMap = new ArrayList<>();
        for (CacheOperation cacheOperation : cacheOperations) {
            Object cachectx = operationContextMethod.invoke(cacheAspectSupport, cacheOperation, method, args, obj, typeClass);
            Object key = generateKeyMethod.invoke(cacheAspectSupport, cachectx, null);
            if (key == null || (key instanceof Optional && !((Optional<?>) key).isPresent()) || String.valueOf(key).isEmpty()) {
                continue;
            }
            Collection<Cache> caches = (Collection<Cache>) getCachesMethod.invoke(cachectx);
            List<Object> keys = new ArrayList<>();
            if (cacheOperation instanceof CacheableOperation) {
                for (Cache cache : caches) {
                    if (!redisCacheCls.isAssignableFrom(cache.getClass())) {
                        throw new TestkitException("un support cacheType, only support RedisCache");
                    }
                    Object invoke = redisCacheGenerateKeyMethod.invoke(cache, key);
                    if (invoke == null) {
                        continue;
                    }
                    keys.add(invoke);
                }
            } else if (cacheOperation instanceof CacheEvictOperation || cacheOperation instanceof CachePutOperation) {
                for (Cache cache : caches) {
                    if (!redisCacheCls.isAssignableFrom(cache.getClass())) {
                        throw new TestkitException("un support cacheType, only support RedisCache");
                    }
                    Object invoke = redisCacheGenerateKeyMethod.invoke(cache, key);
                    if (invoke == null) {
                        continue;
                    }
                    keys.add(invoke);
                }
            } else {
                throw new TestkitException("un support cacheOperation");
            }
            HashMap<String, List> map = new HashMap<>();
            map.put(cacheOperation.getClass().getSimpleName(), keys);
            keyMap.add(map);
        }
        return keyMap.stream().map(new Function<Map<String, List>, Object>() {
            @Override
            public Object apply(Map<String, List> stringListMap) {
                //取出map的第一个key
                String s = stringListMap.keySet().stream().findFirst().orElse(null);
                if (s == null) {
                    return null;
                }
                List value = stringListMap.get(s);
                if (value == null) {
                    return null;
                }
                HashMap<Object, Object> map = new HashMap<>();
                map.put("operation", s);
                map.put("deleteKeys", value);
                return map;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }


    public static class KV implements Serializable {

        private Object key;

        private Object value;

        public static KV build(Object key) {
            KV kv = new KV();
            kv.key = key;
            return kv;
        }

        public static KV build(Object key, Object value) {
            KV kv = new KV();
            kv.key = key;
            kv.value = value;
            return kv;
        }

        public Object getKey() {
            return key;
        }

        public void setKey(Object key) {
            this.key = key;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

    }

}