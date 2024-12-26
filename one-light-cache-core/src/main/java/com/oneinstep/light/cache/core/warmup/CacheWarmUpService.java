package com.oneinstep.light.cache.core.warmup;

import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCacheManager;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

/**
 * 缓存预热服务
 */
@Service
public class CacheWarmUpService {

    @Resource
    private LightCacheManager cacheManager;

    /**
     * 预热缓存
     *
     * @param cacheName 缓存名称
     * @param keys      缓存键
     */
    public void warmUp(String cacheName, List<String> keys) {
        LightCache<?> cache = cacheManager.getCache(cacheName);
        // 并行预热
        keys.parallelStream().forEach(cache::get);
    }

    /**
     * 预热缓存
     *
     * @param cacheName 缓存名称
     * @param keys      缓存键
     */
    public void warmUp(String cacheName, String... keys) {
        warmUp(cacheName, Arrays.asList(keys));
    }
}
