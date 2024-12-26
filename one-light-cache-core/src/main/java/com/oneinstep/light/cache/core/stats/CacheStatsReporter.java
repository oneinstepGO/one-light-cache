package com.oneinstep.light.cache.core.stats;

import com.oneinstep.light.cache.core.LightCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 缓存统计信息报告器
 */
@Component
@Slf4j
public class CacheStatsReporter {

    @Resource
    private LightCacheManager cacheManager;

    @Scheduled(fixedRate = 60000) // 每分钟统计一次
    public void reportStats() {
        // 获取所有缓存的统计信息
        cacheManager.getAllCaches().forEach((name, cache) -> {
            CacheStats stats = cache.stats();
            log.info("Cache stats - name: {}, hit rate: {}, avg load time: {}ms",
                    name,
                    String.format("%.2f%%", stats.getHitRate() * 100),
                    String.format("%.2f", stats.getAvgLoadPenalty()));
        });
    }
}