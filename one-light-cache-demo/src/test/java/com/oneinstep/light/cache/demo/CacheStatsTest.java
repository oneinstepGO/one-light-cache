package com.oneinstep.light.cache.demo;

import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.core.stats.CacheStats;
import com.oneinstep.light.cache.demo.facade.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class CacheStatsTest extends BaseTest {

    @Test
    void testCacheStats() {
        int index = getIndex();
        registerTestCache(index, true);
        LightCache<UserDTO> cache = cacheManager.getCache(CACHE_NAME + index);

        // First access - should be a miss
        String userId = "1";
        UserDTO user1 = cache.get(userId);
        assertNotNull(user1);

        // Second access - should be a hit
        UserDTO user2 = cache.get(userId);
        assertNotNull(user2);

        // Get stats
        CacheStats stats = cache.stats();
        assertEquals(1, stats.getMissCount());
        assertEquals(1, stats.getHitCount());
        assertEquals(0.5, stats.getHitRate()); // 1 hit / 2 total accesses = 0.5
        assertTrue(stats.getAvgLoadPenalty() > 0);
    }

    @Test
    void testRandomExpiry() throws InterruptedException {
        int index = getIndex();
        // Create cache with short expiry and random range
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName(CACHE_NAME + index)
                .initialCapacity(10)
                .maximumSize(100)
                .expireAfterWrite(3000)
                .expireRandomRange(200)
                .fetcher(this::getUser)
                .enableStats(true)
                .buildAndRegister();

        LightCache<UserDTO> cache = cacheManager.getCache(CACHE_NAME + index);

        // Load more items to increase probability of seeing both hits and misses
        int itemCount = 20;
        for (int i = 0; i < itemCount; i++) {
            UserDTO user = cache.get(String.valueOf(i));
            assertNotNull(user);
        }

        CacheStats beforeStats1 = cache.stats();
        log.info("Before stats1 - hits: {}, misses: {}", beforeStats1.getHitCount(), beforeStats1.getMissCount());
        Thread.sleep(1000);

        for (int i = 0; i < itemCount; i++) {
            UserDTO user = cache.get(String.valueOf(i));
            assertNotNull(user);
        }

        CacheStats beforeStats2 = cache.stats();
        log.info("Before stats2 - hits: {}, misses: {}", beforeStats2.getHitCount(), beforeStats2.getMissCount());
        Thread.sleep(2200);
        // Check items - some should still be valid due to random expiry

        CacheStats beforeStats3 = cache.stats();
        log.info("Before stats3 - hits: {}, misses: {}", beforeStats3.getHitCount(), beforeStats3.getMissCount());
        for (int i = 0; i < itemCount; i++) {
            cache.get(String.valueOf(i));
        }

        CacheStats afterStats = cache.stats();

        log.info("After stats - hits: {}, misses: {}", afterStats.getHitCount(), afterStats.getMissCount());

        // Some items should have been hits (still valid) and some misses (expired)
        // assertTrue(afterStats.getHitCount() > beforeStats.getHitCount(), "Expected
        // some cache hits after expiry");
        // assertTrue(afterStats.getMissCount() > beforeStats.getMissCount(), "Expected
        // some cache misses after expiry");
    }
}