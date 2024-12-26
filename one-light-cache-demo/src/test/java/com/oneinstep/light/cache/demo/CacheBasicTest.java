package com.oneinstep.light.cache.demo;

import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.exception.CacheNameExistException;
import com.oneinstep.light.cache.demo.facade.UserDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheBasicTest extends BaseTest {

    @Test
    void testCacheCreation() {
        int index = getIndex();
        registerTestCache(index);
        assertTrue(cacheManager.isCacheExist(CACHE_NAME + index));

        // 测试重复注册
        assertThrows(CacheNameExistException.class, () -> registerTestCache(index));
    }

    @Test
    void testCacheGet() {
        int index = getIndex();
        registerTestCache(index);
        LightCache<UserDTO> cache = cacheManager.getCache(CACHE_NAME + index);

        // 首次获取
        String userId = "1";
        UserDTO user = cache.get(userId);
        assertNotNull(user);
        assertEquals(userId, user.getUserId());

        // 再次获取应该从缓存中获取
        UserDTO cachedUser = cache.get(userId);
        assertNotNull(cachedUser);
        assertEquals(user.getUserName(), cachedUser.getUserName());
    }

    @Test
    void testCacheInvalidate() {
        int index = getIndex();
        registerTestCache(index);
        LightCache<UserDTO> cache = cacheManager.getCache(CACHE_NAME + index);

        String userId = "1";
        UserDTO user1 = cache.get(userId);
        assertNotNull(user1);

        // 使缓存失效
        cache.invalidate(userId);

        // 重新获取应该是新的对象
        UserDTO user2 = cache.get(userId);
        assertNotNull(user2);
        assertEquals(user1.getUserId(), user2.getUserId());
    }
}