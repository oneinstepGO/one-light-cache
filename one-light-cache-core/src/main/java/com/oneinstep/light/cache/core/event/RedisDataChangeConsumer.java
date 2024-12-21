package com.oneinstep.light.cache.core.event;

import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 数据变更消费者
 */
@Slf4j
public class RedisDataChangeConsumer extends AbsDataChangeConsumer {

    /**
     * Redisson 客户端
     */
    private final RedissonClient redissonClient;

    public RedisDataChangeConsumer(String topic, RedissonClient redissonClient) {
        super(topic);
        this.redissonClient = redissonClient;
    }

    @Override
    public void start() {
        try {
            // 获取订阅消息的主题
            RTopic rTopic = redissonClient.getTopic(topic);
            // 订阅消息
            rTopic.addListener(String.class, (charSequence, msg) -> consumeMsg(msg));
        } catch (Exception e) {
            log.error("Failed to subscribe topic", e);
        }
    }

    @Override
    public void stop() {
        this.isRunning = false;
    }

}
