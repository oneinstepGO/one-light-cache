package com.oneinstep.light.cache.demo;

import com.alibaba.fastjson2.JSON;
import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.event.DataChangeMsg;
import com.oneinstep.light.cache.demo.facade.UserDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CacheUpdateTest extends BaseTest {

    @Test
    void testCacheRefresh() throws InterruptedException {
        int index = getIndex();
        registerTestCache(index);
        LightCache<UserDTO> cache = cacheManager.getCache(CACHE_NAME + index);

        String userId = "1";
        UserDTO user1 = cache.get(userId);
        assertNotNull(user1);

        // 模拟发送更新消息
        DataChangeMsg msg = DataChangeMsg.builder()
                .dataName(CACHE_NAME + index)
                .dataId(userId)
                .type(DataChangeMsg.DataChangeType.UPDATE)
                .build();

        // 发送消息到Redis
        redissonClient.getTopic("test_user_topic").publish(JSON.toJSONString(msg));

        // 等待消息处理
        Thread.sleep(1000);

        // 获取更新后的数据
        UserDTO user2 = cache.get(userId);
        assertNotNull(user2);
    }

    @Test
    void testConcurrentCacheAccess() throws InterruptedException {
        int cacheIndex = getIndex();
        registerTestCache(cacheIndex);
        LightCache<UserDTO> cache = cacheManager.getCache(CACHE_NAME + cacheIndex);
        String userId = "1";

        // 创建多个线程同时访问缓存
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        UserDTO[] results = new UserDTO[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = cache.get(userId);
            });
            threads[i].start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证所有线程获取的是相同的数据
        UserDTO firstResult = results[0];
        for (int i = 1; i < threadCount; i++) {
            assertEquals(firstResult.getUserId(), results[i].getUserId());
            assertEquals(firstResult.getUserName(), results[i].getUserName());
        }
    }
}