package com.oneinstep.light.cache.starter.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * LightCache 配置
 */
@ConfigurationProperties(prefix = "light.cache")
@Data
public class LightCacheManagerProperties {
    /**
     * 是否使用 Redis 作为缓存 默认 true
     */
    private boolean useRedisAsCache = true;
    /**
     * RocketMQ 名称服务器
     */
    private String rocketmqNameServer;
    /**
     * Kafka Bootstrap Servers
     */
    private String kafkaBootstrapServers;
    /**
     * 生产者组
     */
    private String producerGroup = "light-cache-producer-group";
    /**
     * 消费者组
     */
    private String consumerGroup = "light-cache-consumer-group";
    /**
     * 缓存配置
     */
    private List<LightCacheProperties> cacheConfigs;

    /**
     * 缓存配置
     */
    @Data
    public static class LightCacheProperties {
        /**
         * 缓存名称
         */
        private String cacheName;
        /**
         * 初始容量
         */
        private Integer initialCapacity = 50;
        /**
         * 最大容量
         */
        private Long maximumSize = 50000L;
        /**
         * 过期时间
         */
        private Long expireAfterWrite = 5000L;
        /**
         * 刷新时间
         */
        private Long refreshAfterWrite = 86400000L;
        /**
         * 加载缓存表达式
         */
        private String loadCacheExpression;
        /**
         * 加载缓存等待锁超时时间
         */
        private Long loadCacheWaitLockTimeout = 3000L;
        /**
         * 获取数据超时时间
         */
        private Long fetchDataTimeout = 5000L;
        /**
         * mq类型
         */
        private String mqType = "ROCKETMQ";
        /**
         * mq���题
         */
        private String mqTopic;
    }

}