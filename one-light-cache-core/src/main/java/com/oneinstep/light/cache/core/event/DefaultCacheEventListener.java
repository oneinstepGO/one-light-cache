package com.oneinstep.light.cache.core.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认缓存事件监听器
 */
@Slf4j
@Component
public class DefaultCacheEventListener implements CacheEventListener {

    @Override
    public void onPut(String cacheName, String key, Object value) {
        log.debug("Cache put - cacheName: {}, key: {}", cacheName, key);
    }

    @Override
    public void onRemove(String cacheName, String key) {
        log.debug("Cache remove - cacheName: {}, key: {}", cacheName, key);
    }

    @Override
    public void onExpire(String cacheName, String key) {
        log.debug("Cache expire - cacheName: {}, key: {}", cacheName, key);
    }
} 