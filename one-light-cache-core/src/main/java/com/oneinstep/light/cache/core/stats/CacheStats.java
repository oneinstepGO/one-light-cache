package com.oneinstep.light.cache.core.stats;

import lombok.Data;

/**
 * 缓存统计信息
 */
@Data
public class CacheStats {
    private String cacheName; // 缓存名称
    private long hitCount; // 命中次数
    private long missCount; // 未命中次数
    private long loadSuccessCount; // 加载成功次数
    private long loadFailureCount; // 加载失败次数
    private double hitRate; // 命中率
    private double avgLoadPenalty; // 平均加载耗时

    public static CacheStats from(String cacheName, com.github.benmanes.caffeine.cache.stats.CacheStats caffeineStats) {
        CacheStats stats = new CacheStats();
        stats.setCacheName(cacheName);
        stats.setHitCount(caffeineStats.hitCount());
        stats.setMissCount(caffeineStats.missCount());
        stats.setLoadSuccessCount(caffeineStats.loadSuccessCount());
        stats.setLoadFailureCount(caffeineStats.loadFailureCount());
        stats.setHitRate(caffeineStats.hitRate());
        stats.setAvgLoadPenalty(caffeineStats.averageLoadPenalty());
        return stats;
    }
}