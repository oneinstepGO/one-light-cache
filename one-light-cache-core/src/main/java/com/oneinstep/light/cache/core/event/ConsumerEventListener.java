package com.oneinstep.light.cache.core.event;

import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.context.event.EventListener;

import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCacheManager;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 消费者事件监听器
 */
@Slf4j
public class ConsumerEventListener {

    @Resource
    private LightCacheManager lightCacheManager;

    @EventListener
    public void handleMyCustomEvent(ConsumerCreateEvent event) {
        String dataChangeTopic = event.getDataChangeTopic();
        LightCache.MQType mqType = event.getMqType();
        String cacheName = event.getCacheName();
        log.info("Received event: cacheName={}, dataChangeTopic={}, mqType={}", cacheName, dataChangeTopic, mqType);
        if (LightCache.MQType.NO_MQ.equals(mqType) || StringUtils.isBlank(dataChangeTopic) || StringUtils.isBlank(cacheName)) {
            log.info("No need to subscribe topic, because mqType is NO_MQ or topic or cacheName is blank");
            return;
        }

        AbsDataChangeConsumer consumer = null;
        // 订阅主题
        try {
            if (mqType == LightCache.MQType.ROCKETMQ) {
                String consumerGroup = lightCacheManager.getConsumerGroup();
                String namesrvAddr = lightCacheManager.getRocketmqNameServer();
                consumer = new RocketMQDataChangeConsumer(namesrvAddr, dataChangeTopic, consumerGroup);
            } else {
                RedissonClient redissonClient = lightCacheManager.getRedissonClient();
                if (redissonClient != null) {
                    consumer = new RedisDataChangeConsumer(dataChangeTopic, redissonClient);
                } else {
                    log.error("Failed to create consumer, redissonClient is null");
                }
            }

            if (consumer == null) {
                log.error("Failed to create consumer, mqType: {}", mqType);
                return;
            }

            // 设置消费者
            lightCacheManager.setConsumer(cacheName, consumer);
            consumer.start();

        } catch (Exception e) {
            log.error("Failed to subscribe topic", e);
        }
    }
}