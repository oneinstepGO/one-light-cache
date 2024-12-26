package com.oneinstep.light.cache.demo;

import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.demo.facade.UserDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CacheExpiryTest extends BaseTest {

    @Test
    void testCacheExpiry() throws InterruptedException {
        int index = getIndex();
        // 注册一个短期过期的缓存
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName(CACHE_NAME + index)
                .initialCapacity(10)
                .maximumSize(100)
                .expireAfterWrite(1000) // 1秒后过期
                .fetcher(userId -> UserDTO.builder()
                        .userId(userId)
                        .userName("user-" + userId + "-" + System.currentTimeMillis())
                        .build())
                .buildAndRegister();

        LightCache<UserDTO> cache = cacheManager.getCache(CACHE_NAME + index);

        String userId = "1";
        UserDTO user1 = cache.get(userId);
        assertNotNull(user1);

        // 等待缓存过期
        Thread.sleep(1500);

        // 重新获取应该是新的对象
        UserDTO user2 = cache.get(userId);
        assertNotNull(user2);
        assertEquals(user1.getUserId(), user2.getUserId());
    }

    @Test
    void testCacheReload() {
        int index = getIndex();
        // 使用自定义的数据加载器
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName(CACHE_NAME + index)
                .initialCapacity(10)
                .maximumSize(100)
                .expireAfterWrite(5000)
                .fetcher(userId -> UserDTO.builder()
                        .userId(userId)
                        .userName("Reloaded User " + userId)
                        .build())
                .buildAndRegister();

        LightCache<UserDTO> cache = cacheManager.getCache(CACHE_NAME + index);

        String userId = "1";
        UserDTO user = cache.get(userId);
        assertNotNull(user);
        assertEquals("Reloaded User " + userId, user.getUserName());
    }
}
