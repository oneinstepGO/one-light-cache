package com.oneinstep.light.cache.demo;

import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.demo.facade.UserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class BaseTest {

    protected RedissonClient redissonClient;
    protected LightCacheManager cacheManager;
    protected static final String CACHE_NAME = "test-user-cache-";

    private static final AtomicInteger CACHE_INDEX = new AtomicInteger(0);

    @BeforeEach
    public void setUp() {
        // 初始化 Redisson
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379");
        redissonClient = Redisson.create(config);

        // 初始化缓存管理器
        cacheManager = LightCacheManager.getInstance();
        cacheManager.init(true, redissonClient, "localhost:9876", "test-consumer-group");
    }

    public static int getIndex() {
        return CACHE_INDEX.getAndIncrement();
    }

    protected void registerTestCache(int index, boolean enableStats) {
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName(CACHE_NAME + index)
                .initialCapacity(10)
                .maximumSize(100)
                .expireAfterWrite(5000)
                .fetcher(this::getUser)
                // .loadCacheExpression("getUserById(userId)")
                .mqTopic("test_user_topic_" + index)
                .mqType(LightCache.MQType.REDIS)
                .enableStats(enableStats)
                .buildAndRegister();
    }

    protected void registerTestCache(int index) {
        registerTestCache(index, false);
    }

    protected UserDTO getUser(String userId) {
        return UserDTO.builder()
                .userId(userId)
                .userName("test-" + userId)
                .createTime(LocalDateTime.now())
                .build();
    }
}
