package com.oneinstep.light.cache.core.event;

import org.springframework.context.ApplicationEvent;

import com.oneinstep.light.cache.core.LightCache;

import lombok.Getter;

/**
 * 消费者创建事件
 */
@Getter
public class ConsumerCreateEvent extends ApplicationEvent {

    private final String cacheName;
    private final String dataChangeTopic;
    private final LightCache.MQType mqType;

    public ConsumerCreateEvent(Object source, String cacheName, String dataChangeTopic, LightCache.MQType mqType) {
        super(source);
        this.cacheName = cacheName;
        this.dataChangeTopic = dataChangeTopic;
        this.mqType = mqType;
    }

}
