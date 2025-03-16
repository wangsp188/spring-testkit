package com.testkit.side_server;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.*;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SpringCacheTool {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CacheAspectSupport cacheAspectSupport;


    @Autowired
    private CacheOperationSource cacheOperationSource;


    Method operationContextMethod;
    Method generateKeyMethod;
    Method getCachesMethod;
    Method redisCacheGenerateKeyMethod;
    Class<?> redisCacheCls;

    Class<?> colWrapCls;
    Method colWrapClsAsColMethod;


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

            try {
                Class<?> cls = Class.forName("com.zoom.contactcenter.cache.v2.redis_v2.CollectionWrapper");
                colWrapClsAsColMethod = cls.getDeclaredMethod("asCollection");
                colWrapCls = cls;
            } catch (Throwable e) {
            }


        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public Object process(Map<String, String> params) throws Exception {

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
        ReflexBox reflexBox = ReflexUtils.parse(typeClass, methodName, methodArgTypesStr, methodArgsStr);
        Object bean = null;
        if (beanName == null || beanName.trim().isEmpty()) {
            Map<String, ?> beansOfType = applicationContext.getBeansOfType(typeClass);
            if (beansOfType.isEmpty()) {
                throw new TestkitException("can not find " + typeClass + " in this spring");
            } else if (beansOfType.size() > 1) {
                throw new TestkitException("no union bean of type " + typeClass + " in this spring");
            }
            bean = beansOfType.values().iterator().next();
        } else {
            try {
                bean = applicationContext.getBean(beanName, typeClass);
            } catch (BeansException e) {
                throw new TestkitException("can not find " + typeClass + " in this spring," + e.getMessage());
            }
        }
        return buildKey(typeClass, reflexBox.getMethod(), bean, reflexBox.getArgs(), action);
    }


    public List buildKey(Class typeClass, Method method, Object obj, Object[] args, String action) throws Exception {
        if (operationContextMethod == null || generateKeyMethod == null) {
            throw new TestkitException("operationContextMethod or generateKeyMethod is null");
        }
        if (!cacheOperationSource.isCandidateClass(typeClass)) {
            throw new TestkitException("typeClass is not Cache");
        }
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
                    if (colWrapCls != null && colWrapCls.isAssignableFrom(key.getClass())) {
                        for (Object o : (Collection) colWrapClsAsColMethod.invoke(key)) {
                            Object invoke = redisCacheGenerateKeyMethod.invoke(cache, o);
                            if (invoke == null) {
                                continue;
                            }
                            if ("build_cache_key".equals(action)) {
                                keys.add(KV.build(invoke.toString()));
                            } else if ("get_cache".equals(action)) {
                                Cache.ValueWrapper valueWrapper = cache.get(o);
                                keys.add(KV.build(invoke, valueWrapper == null ? null : valueWrapper.get()));
                            } else {
                                keys.add(KV.build(invoke));
                            }
                        }
                        continue;
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