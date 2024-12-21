package com.oneinstep.light.cache.demo;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCache.MQType;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.core.exception.CacheNameExistException;
import com.oneinstep.light.cache.demo.bean.User;
import com.oneinstep.light.cache.demo.facade.UserDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class LightCacheNoSpringTest {

    @BeforeEach
    void beforeEach() {
        LightCacheManager lightCacheManager = LightCacheManager.getInstance();
        lightCacheManager.init(
                false,
                null,
                null,
                "data-change-consume-group");

    }

    // 应该抛出异常 CacheNameExistException
    @Test
    void testDuplicationCacheName() {

        Assertions.assertThrowsExactly(CacheNameExistException.class, () -> {
            // 重复的缓存名称
            LightCacheManager.<User>newCacheBuilder()
                    .cacheName("user")
                    .initialCapacity(20)
                    .maximumSize(100)
                    .expireAfterWrite(5000)
                    .fetcher(userIdStr -> new User())
                    .mqTopic("user_data_change1")
                    .mqType(MQType.ROCKETMQ)
                    .buildAndRegister();

            LightCacheManager.<User>newCacheBuilder()
                    .cacheName("user")
                    .initialCapacity(10)
                    .maximumSize(100)
                    .expireAfterWrite(5000)
                    .fetcher(userIdStr -> new User())
                    .mqTopic("user_data_change2")
                    .buildAndRegister();
        });

    }

    @Test
    void testCacheExpire() {
        LightCacheManager.<User>newCacheBuilder()
                .cacheName("test-user-direct")
                .initialCapacity(20)
                .maximumSize(100)
                .expireAfterWrite(5000)
                .fetcher(userIdStr -> User.builder().userId(Long.parseLong(userIdStr))
                        .userName("user-" + System.currentTimeMillis()).build())
                .mqTopic("user_data_change")
                .buildAndRegister();

        LightCache<User> cache = LightCacheManager.getInstance().getCache("test-user-direct");
        User user1 = cache.get("1");
        log.info("user1: {}", user1);
        try {
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        User user2 = cache.get("1");
        log.info("user2: {}", user2);

        assertNotNull(user1);
        assertNotNull(user2);
        Assertions.assertNotEquals(user1, user2);
    }

    @Test
    void testCreateCacheWithExpression() {
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName("test-user-expression")
                .initialCapacity(20)
                .maximumSize(100)
                .expireAfterWrite(5000)
                .loadCacheExpression("return key + \"-User\";")
                .mqTopic("user_data_change")
                .buildAndRegister();

        LightCache<String> cache = LightCacheManager.getInstance().getCache("test-user-expression");
        assertNotNull(cache);
        String value = cache.get("1");
        log.info("value: {}", value);
        assertNotNull(value);
        Assertions.assertEquals("1-User", value);

        String value2 = cache.get("2");
        log.info("value2: {}", value2);
        assertNotNull(value2);
        Assertions.assertEquals("2-User", value2);
    }

}