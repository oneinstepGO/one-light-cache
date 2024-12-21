package com.oneinstep.light.cache.core;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;

import com.oneinstep.light.cache.core.event.AbsDataChangeConsumer;
import com.oneinstep.light.cache.core.event.ConsumerEventPublisher;
import com.oneinstep.light.cache.core.exception.CacheNameExistException;
import com.oneinstep.light.cache.core.exception.LightCacheException;
import com.oneinstep.light.cache.core.util.SpringBeanUtil;

import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 缓存管理器 单例模式
 */
@Slf4j
public class LightCacheManager {

    private LightCacheManager() {
    }

    // 单例
    private static volatile LightCacheManager instance;

    // 缓存容器
    private static final Map<String, LightCache<?>> ALL_CACHE = new ConcurrentHashMap<>();

    // 缓存消费者 key: cacheName value: consumer
    private static final Map<String, AbsDataChangeConsumer> CACHE_CONSUMER = new ConcurrentHashMap<>();

    // 是否初始化
    @Getter
    private static volatile boolean isInit = false;

    // Redisson客户端
    @Getter
    private RedissonClient redissonClient;

    // RocketMQ NameServer
    @Getter
    private String rocketmqNameServer;

    // 消费者组
    @Getter
    private String consumerGroup;

    // 是否使用Redis作为缓存
    @Getter
    private boolean useRedisAsCache = true;

    /**
     * 初始化
     *
     * @param useRedisAsCache    是否使用Redis作为缓存
     * @param redissonClient     Redisson客户端
     * @param rocketmqNameServer RocketMQ NameServer
     * @param consumerGroup      消费者组
     */
    public synchronized void init(boolean useRedisAsCache, RedissonClient redissonClient, String rocketmqNameServer,
                                  String consumerGroup) {
        if (isInit) {
            return;
        }
        this.useRedisAsCache = useRedisAsCache;
        this.redissonClient = redissonClient;
        if (this.useRedisAsCache && this.redissonClient == null) {
            log.warn("Redisson client is null");
            throw new IllegalArgumentException("If you use Redis as cache, you must provide a Redisson client");
        }
        this.rocketmqNameServer = rocketmqNameServer;
        this.consumerGroup = consumerGroup;
        isInit = true;
        log.info("LightCacheManager initialized");
    }

    /**
     * 获取实例
     *
     * @return LightCacheManager 单例
     */
    public static LightCacheManager getInstance() {
        if (instance == null) {
            synchronized (LightCacheManager.class) {
                if (instance == null) {
                    instance = new LightCacheManager();
                }
            }
        }
        return instance;
    }

    /**
     * 注册缓存
     *
     * @param cache 缓存
     */
    public void registerCache(@Nonnull LightCache<?> cache) {
        if (!isInit) {
            log.warn("LightCacheManager is not initialized");
            throw new IllegalArgumentException("LightCacheManager is not initialized");
        }
        final String cacheName = cache.getCacheName();
        if (ALL_CACHE.containsKey(cacheName)) {
            log.warn("Cache already exists: {}", cacheName);
            throw new CacheNameExistException(cacheName);
        }

        boolean success = ALL_CACHE.putIfAbsent(cacheName, cache) == null;
        if (!success) {
            log.warn("Failed to register cache: {}", cacheName);
            throw new CacheNameExistException(cacheName);
        }
        log.info("Register cache: {}", cacheName);
        // 启动消费者
        if (StringUtils.isNotBlank(cache.getDataChangeTopic()) && !cache.getMqType().equals(LightCache.MQType.NO_MQ)) {
            // 订阅数据变更
            addCacheTopic(cacheName, cache.getDataChangeTopic(), cache.getMqType());
        }
    }

    /**
     * 获取缓存
     *
     * @param cacheName 缓存名称
     * @return 缓存
     */
    public <T> LightCache<T> getCache(@Nonnull String cacheName) {
        if (!isInit) {
            log.warn("LightCacheManager is not initialized");
            throw new IllegalArgumentException("LightCacheManager is not initialized");
        }
        @SuppressWarnings("unchecked")
        LightCache<T> cache = (LightCache<T>) ALL_CACHE.get(cacheName);
        if (cache == null) {
            log.warn("Cache not found: {}", cacheName);
            throw new IllegalArgumentException("Cache not found: " + cacheName);
        }
        return cache;
    }

    /**
     * 是否存在缓存
     *
     * @param cacheName 缓存名称
     * @return 是否存在
     */
    public boolean isCacheExist(@Nonnull String cacheName) {
        return ALL_CACHE.containsKey(cacheName);
    }

    /**
     * 添加缓存消费者
     *
     * @param cacheName 缓存名称
     * @param consumer  消费者
     */
    public void registerConsumer(String cacheName, AbsDataChangeConsumer consumer) {
        if (!isInit) {
            log.warn("LightCacheManager is not initialized");
            throw new IllegalArgumentException("LightCacheManager is not initialized");
        }
        if (StringUtils.isBlank(cacheName)) {
            log.warn("Cache name is blank");
            throw new IllegalArgumentException("Cache name is blank");
        }
        if (consumer == null) {
            log.warn("Consumer is null");
            throw new IllegalArgumentException("Consumer is null");
        }
        AbsDataChangeConsumer changeConsumer = CACHE_CONSUMER.putIfAbsent(cacheName, consumer);
        if (changeConsumer != null) {
            log.warn("Consumer already exists: {}", cacheName);
            throw new IllegalArgumentException("Consumer already exists: " + cacheName);
        }
    }

    /**
     * 销毁
     */
    public void destroy() {
        if (!isInit) {
            log.warn("LightCacheManager is not initialized");
            throw new IllegalArgumentException("LightCacheManager is not initialized");
        }
        try {
            // 关闭消费者
            CACHE_CONSUMER.values().stream().filter(Objects::nonNull).forEach(AbsDataChangeConsumer::stop);
            CACHE_CONSUMER.clear();

            // 关闭缓存
            ALL_CACHE.clear();
        } catch (Exception e) {
            log.error("Failed to close consumer", e);
        }
    }

    /**
     * 添加缓存订阅主题
     *
     * @param cacheName       缓存名称
     * @param dataChangeTopic 数据变更主题
     */
    private void addCacheTopic(String cacheName, String dataChangeTopic, LightCache.MQType mqType) {
        // 发送事件通知
        ConsumerEventPublisher eventPublisher = SpringBeanUtil.getBean(ConsumerEventPublisher.class);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(cacheName, dataChangeTopic, mqType);
        }
    }

    /**
     * 为缓存设置消费者
     *
     * @param cacheName 缓存名称
     * @param consumer  消费者
     */
    public void setConsumer(@Nonnull String cacheName, AbsDataChangeConsumer consumer) {
        CACHE_CONSUMER.putIfAbsent(cacheName, consumer);
    }

    /**
     * 创建缓存构建器
     *
     * @param <T> 缓存数据类型
     * @return LightCache.CacheBuilder
     */
    public static <T> LightCache.CacheBuilder<T> newCacheBuilder() {
        if (!isInit) {
            log.warn("LightCacheManager is not initialized");
            throw new LightCacheException("LightCacheManager is not initialized");
        }
        return new LightCache.CacheBuilder<>();
    }

}
