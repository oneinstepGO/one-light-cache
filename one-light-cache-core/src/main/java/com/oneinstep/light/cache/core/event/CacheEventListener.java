package com.oneinstep.light.cache.core.event;

/**
 * 缓存事件监听器
 */
public interface CacheEventListener {
    /**
     * 缓存put事件
     *
     * @param cacheName 缓存名称
     * @param key       缓存key
     * @param value     缓存value
     */
    void onPut(String cacheName, String key, Object value);

    /**
     * 缓存remove事件
     *
     * @param cacheName 缓存名称
     * @param key       缓存key
     */
    void onRemove(String cacheName, String key);

    /**
     * 缓存过期事件
     *
     * @param cacheName 缓存名称
     * @param key       缓存key
     */
    void onExpire(String cacheName, String key);
} 