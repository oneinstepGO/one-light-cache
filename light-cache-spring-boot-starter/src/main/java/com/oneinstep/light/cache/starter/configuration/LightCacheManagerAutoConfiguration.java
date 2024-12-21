package com.oneinstep.light.cache.starter.configuration;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.starter.producer.RedisDataChangeMsgProducer;
import com.oneinstep.light.cache.starter.producer.RocketMQDataChangeMsgProducer;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * LightCacheManager 自动配置
 */
@Slf4j
@Configuration
@ConditionalOnClass(Caffeine.class)
@EnableConfigurationProperties(LightCacheManagerProperties.class)
public class LightCacheManagerAutoConfiguration {

    private final LightCacheManagerProperties properties;

    public LightCacheManagerAutoConfiguration(LightCacheManagerProperties properties) {
        this.properties = properties;
    }

    /**
     * 配置 LightCacheManager
     *
     * @param redissonClient RedissonClient
     * @param environment    环境变量
     * @return LightCacheManager
     */
    @Bean
    @ConditionalOnMissingBean
    public LightCacheManager configureLightCacheManager(RedissonClient redissonClient, Environment environment) {
        String rocketmqNameServer = getNameServer(environment);
        // 获取 LightCacheManager 实例
        LightCacheManager manager = LightCacheManager.getInstance();
        // 调用 init 方法初始化 LightCacheManager
        manager.init(properties.isUseRedisAsCache(), redissonClient, rocketmqNameServer, properties.getConsumerGroup());
        return manager;
    }

    /**
     * 注册 RedisDataChangeMsgProducer
     *
     * @param redissonClient RedissonClient
     * @return RedisDataChangeMsgProducer
     */
    @ConditionalOnBean(RedissonClient.class)
    public RedisDataChangeMsgProducer redisDataChangeMsgProducer(RedissonClient redissonClient) {
        return new RedisDataChangeMsgProducer(redissonClient);
    }

    /**
     * 注册 RocketMQDataChangeMsgProducer
     *
     * @param environment 环境变量
     * @return RocketMQDataChangeMsgProducer
     */
    @Bean(initMethod = "init", destroyMethod = "destroy")
    @ConditionalOnClass(DefaultMQProducer.class)
    public RocketMQDataChangeMsgProducer rocketMQDataChangeMsgProducer(Environment environment) {
        String rocketmqNameServer = getNameServer(environment);
        properties.setRocketmqNameServer(rocketmqNameServer);
        return new RocketMQDataChangeMsgProducer(properties);
    }

    /**
     * 销毁 LightCacheManager
     */
    @PreDestroy
    public void destroy() {
        LightCacheManager.getInstance().destroy();
    }

    private String getNameServer(Environment environment) {
        String rocketmqNameServer = this.properties.getRocketmqNameServer();
        if (StringUtils.isBlank(rocketmqNameServer)) {
            log.warn("rocketmqNameServer is not set, use default value: {}",
                    environment.getProperty("rocketmq.name-server", "localhost:9876"));
            rocketmqNameServer = environment.getProperty("rocketmq.name-server", "localhost:9876");
        }
        return rocketmqNameServer;
    }


}