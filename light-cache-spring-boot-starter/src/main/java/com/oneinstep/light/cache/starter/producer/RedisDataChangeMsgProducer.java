package com.oneinstep.light.cache.starter.producer;

import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 数据变更消息生产者
 * 建议使用 RocketMQ 发送消息，因为 Redisson 的消息发布订阅功能
 */
@Slf4j
public class RedisDataChangeMsgProducer implements IDataChangeMsgProducer {

    private final RedissonClient redissonClient;

    public RedisDataChangeMsgProducer(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public void sendMsg(String topic, String message) {
        try {
            // 获取发布消息的主题
            RTopic rTopic = redissonClient.getTopic(topic);
            // 发布消息
            rTopic.publish(message);
        } catch (Exception e) {
            log.error("Failed to push message", e);
        }
    }

}
