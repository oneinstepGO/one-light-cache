package com.oneinstep.light.cache.demo;

import com.alibaba.fastjson2.JSON;
import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.core.event.DataChangeMsg;
import com.oneinstep.light.cache.core.event.RedisDataChangeConsumer;
import com.oneinstep.light.cache.demo.facade.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class CacheDelayDeleteTest extends BaseTest {

    @Test
    void testDelayDelete() throws InterruptedException {
        int index = getIndex();
        String userId = "test-delay-delete-" + index;
        String cacheName = CACHE_NAME + index;
        String topic = "test_user_topic_" + index;

        // 注册缓存，使用带时间戳的用户名以确保每次获取的实例都不同
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName(cacheName)
                .initialCapacity(10)
                .maximumSize(100)
                .expireAfterWrite(5000)
                .fetcher(key -> UserDTO.builder()
                        .userId(key)
                        .userName("user-" + key + "-" + System.currentTimeMillis())
                        .build())
                .mqTopic(topic)
                .mqType(LightCache.MQType.REDIS)
                .buildAndRegister();

        // 创建并启动消费者
        RedisDataChangeConsumer consumer = new RedisDataChangeConsumer(topic, redissonClient);
        cacheManager.setConsumer(cacheName, consumer);
        consumer.start();

        LightCache<UserDTO> cache = cacheManager.getCache(cacheName);

        // 首次获取，确保数据在缓存中
        UserDTO user1 = cache.get(userId);
        assertNotNull(user1);
        String originalName = user1.getUserName();
        log.info("First fetch - user name: {}", originalName);

        // 验证Redis中有缓存数据
        String redisKey = "CACHE:" + cacheName + ":" + userId;
        RBucket<byte[]> bucket = redissonClient.getBucket(redisKey);
        assertNotNull(bucket.get(), "Redis should have initial value cached");
        log.info("Verified initial value is cached in Redis");

        // 等待一段时间确保缓存已经稳定
        Thread.sleep(100);

        // 模拟数据库更新
        DataChangeMsg msg = DataChangeMsg.builder()
                .dataName(cacheName)
                .dataId(userId)
                .type(DataChangeMsg.DataChangeType.UPDATE)
                .build();

        // 发送更新消息
        redissonClient.getTopic(topic).publish(JSON.toJSONString(msg));
        log.info("Published update message to topic: {}", topic);

        // 等待第一次删除完成
        Thread.sleep(200);

        // 验证本地缓存和Redis缓存是否已被删除（第一次删除）
        assertNull(bucket.get(), "Redis cache should be deleted after first delete");
        log.info("Verified Redis cache is deleted after first delete");

        // 等待延时删除消息处理
        Thread.sleep(600); // 等待超过延时时间(500ms)

        // 验证延时删除后Redis缓存仍然为空（第二次删除）
        assertNull(bucket.get(), "Redis cache should still be deleted after delay delete");
        log.info("Verified Redis cache is still deleted after delay delete");

        // 再次获取缓存，应该是新的数据
        UserDTO user2 = cache.get(userId);
        assertNotNull(user2);
        log.info("Second fetch - user name: {}", user2.getUserName());

        // 验证新数据已被缓存到Redis
        assertNotNull(bucket.get(), "Redis should have new value cached");
        log.info("Verified new value is cached in Redis");

        assertNotEquals(originalName, user2.getUserName(),
                "Should get a new instance with different timestamp after delay delete");
    }

    @Test
    void testConcurrentDelayDelete() throws InterruptedException {
        int index = getIndex();
        String userId = "test-concurrent-" + index;

        // 注册缓存
        registerTestCache(index);
        LightCache<UserDTO> cache = cacheManager.getCache(CACHE_NAME + index);

        // 首次获取，确保数据在缓存中
        UserDTO initialUser = cache.get(userId);
        assertNotNull(initialUser);

        // 创建并发测试环境
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // 启动多个线程同时进行缓存操作
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待所有线程就绪

                    // 模拟数据库更新和缓存操作
                    DataChangeMsg msg = DataChangeMsg.builder()
                            .dataName(CACHE_NAME + index)
                            .dataId(userId)
                            .type(DataChangeMsg.DataChangeType.UPDATE)
                            .build();

                    String topic = "test_user_topic_" + index;
                    redissonClient.getTopic(topic).publish(JSON.toJSONString(msg));

                    // 尝试获取缓存
                    UserDTO user = cache.get(userId);
                    if (user != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Error in concurrent test", e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 开始并发测试
        startLatch.countDown();

        // 等待所有线程完成
        assertTrue(endLatch.await(5, TimeUnit.SECONDS), "Concurrent test timeout");

        // 验证最终结果
        assertTrue(successCount.get() > 0, "Some cache operations should succeed");

        // 清理线程池
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor shutdown timeout");
    }

    @Test
    void testDelayDeleteWithNullValue() throws InterruptedException {
        int index = getIndex();
        String userId = "test-null-" + index;
        String cacheName = CACHE_NAME + index;
        String topic = "test_user_topic_" + index;

        // 注册缓存，使用返回null的fetcher
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName(cacheName)
                .initialCapacity(10)
                .maximumSize(100)
                .expireAfterWrite(5000)
                .fetcher(key -> null) // 始终返回null
                .mqTopic(topic)
                .mqType(LightCache.MQType.REDIS)
                .buildAndRegister();

        // 创建并启动消费者
        RedisDataChangeConsumer consumer = new RedisDataChangeConsumer(topic, redissonClient);
        cacheManager.setConsumer(cacheName, consumer);
        consumer.start();

        LightCache<UserDTO> cache = cacheManager.getCache(cacheName);

        // 首次获取，应该返回null并缓存null值
        UserDTO user1 = cache.get(userId);
        assertNull(user1, "First fetch should return null");

        // 验证Redis中已缓存空值
        String redisKey = "CACHE:" + cacheName + ":" + userId;
        RBucket<byte[]> bucket = redissonClient.getBucket(redisKey);
        byte[] value = bucket.get();
        assertNotNull(value, "Redis should have null value cached");
        log.info("Verified null value is cached in Redis, value length: {}", value.length);

        // 发送更新消息
        DataChangeMsg msg = DataChangeMsg.builder()
                .dataName(cacheName)
                .dataId(userId)
                .type(DataChangeMsg.DataChangeType.UPDATE)
                .build();

        redissonClient.getTopic(topic).publish(JSON.toJSONString(msg));
        log.info("Published update message to topic: {}", topic);

        // 等待消息处理和延时删除
        Thread.sleep(800); // 等待超过延时时间(500ms)

        // 再次获取，应该仍然是null
        UserDTO user2 = cache.get(userId);
        assertNull(user2, "Second fetch should still return null");

        // 验证Redis中仍然有空值缓存
        value = bucket.get();
        assertNotNull(value, "Redis should still have null value cached after update");
        log.info("Verified null value is still cached in Redis after update, value length: {}", value.length);
    }

} 