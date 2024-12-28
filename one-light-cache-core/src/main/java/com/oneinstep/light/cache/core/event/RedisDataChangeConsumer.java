package com.oneinstep.light.cache.core.event;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

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
            rTopic.addListener(String.class, (charSequence, msg) -> {
                try {
                    consumeMsg(msg);
                } catch (Exception e) {
                    log.error("Failed to process message", e);
                    // 消费失败，重试，将消息重新放入队列
                    rTopic.publish(msg);
                }
            });
        } catch (Exception e) {
            log.error("Failed to subscribe topic", e);
        }
    }

    @Override
    public void stop() {
        this.isRunning = false;
    }

}
