package com.oneinstep.light.cache.core.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.oneinstep.light.cache.core.LightCache;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 消费者事件发布器
 */
@Slf4j
@Component
public class ConsumerEventPublisher {

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    public void publishEvent(String cacheName, String dataChangeTopic, LightCache.MQType mqType) {
        ConsumerCreateEvent event = new ConsumerCreateEvent(this, cacheName, dataChangeTopic, mqType);
        applicationEventPublisher.publishEvent(event);
    }

}