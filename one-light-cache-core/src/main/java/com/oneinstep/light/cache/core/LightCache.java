package com.oneinstep.light.cache.core;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.oneinstep.light.cache.core.exception.CacheNameExistException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * 本地缓存
 * 使用 LightCache 前，必须向 LightCacheManager 注册缓存
 * 使用 Caffeine 作为本地缓存，Redis 作为远程缓存来协调一致性以及防止缓存击穿
 * 当DB数据变更时，通过 订阅消息 通知本地更新 Redis 缓存，以及使本地缓存失效
 * 暂时支持两种 通知方式
 * 1、RocketMQ 订阅消息
 * 2、Redis 订阅消息
 *
 * @param <V> 缓存数据类型
 */
@Slf4j
public class LightCache<V> {

    /**
     * 一直等待
     */
    public static final Long WAIT_FOREVER = -1L;

    /**
     * 默认获取数据超时时间
     */
    public static final long DEFAULT_FETCH_DATA_TIMEOUT = 5000L;

    /**
     * 最大获取数据超时时间
     */
    public static final long MAX_FETCH_DATA_TIMEOUT = 15000L;

    /**
     * 缓存名
     */
    @Getter
    private final String cacheName;

    /**
     * 写入后过期时间
     */
    private final long expireAfterWrite;

    /**
     * 构建缓存等待锁超时时间，超过这个时间，如果某个线程还未获取到锁（可能由于其它线程正在构建Redis中的缓存），直接返回null
     * -1 表示不限制超时时间，可能会导致大量线程阻塞在这里，谨慎设置！！！
     */
    private final long loadCacheWaitLockTimeout;

    /**
     * 获取数据超时时间
     * 超过该时间后，缓存加载失败，将设置null值
     * -1 表示不限制超时时间，但是该值仍然会被设置为 MAX_FETCH_DATA_TIMEOUT
     */
    private final long fetchDataTimeout;

    /**
     * 获取缓存数据的函数
     */
    private Function<String, V> fetcher;

    /**
     * 内部caffeine缓存
     */
    private final LoadingCache<String, V> caffeine;

    /**
     * 编译后的表达式
     */
    private Expression compiledExp;

    /**
     * 是否通过表达式获取缓存数据
     */
    private boolean fetchDataByExpression = false;

    /**
     * 通过Spring Boot配置创建缓存
     */
    public static <T> void createFromProperties(String cacheName,
                                                String loadCacheExpression,
                                                int initialCapacity,
                                                long maximumSize,
                                                long expireAfterWrite,
                                                long refreshAfterWrite,
                                                long loadCacheWaitLockTimeout,
                                                long fetchDataTimeout,
                                                MQType mqType,
                                                String mqTopic) {

        // 创建并注册缓存
        LightCacheManager.<T>newCacheBuilder()
                .cacheName(cacheName)
                .initialCapacity(initialCapacity)
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWrite)
                .refreshAfterWrite(refreshAfterWrite)
                .loadCacheWaitLockTimeout(loadCacheWaitLockTimeout)
                .mqType(mqType)
                .mqTopic(mqTopic)
                .loadCacheExpression(loadCacheExpression)
                .fetchDataTimeout(fetchDataTimeout)
                .buildAndRegister();
    }

    /**
     * 消息队列类型 用于通知本地缓存更新
     * 默认使用 RocketMQ，推荐使用 RocketMQ，因为 redis 的消息发布订阅功能不够完善，可能会丢失消息
     */
    @Getter
    private MQType mqType;

    /**
     * 数据变更消息队列主题
     */
    @Getter
    private final String dataChangeTopic;

    /**
     * 是否使用Redis作为缓存
     */
    private final boolean useRedisAsCache;

    /**
     * Redisson客户端
     */
    private final RedissonClient redissonClient;

    /**
     * 获取缓存
     *
     * @param key 缓存key
     * @return 缓存数据
     */
    public V get(String key) {
        checkRegister();
        return caffeine.get(key);
    }

    /**
     * 使缓存失效
     *
     * @param key 缓存key
     */
    public void invalidate(String key) {
        checkRegister();
        caffeine.invalidate(key);
    }

    /**
     * 收到更新消息时，刷新本地缓存
     *
     * @param key      缓存key
     * @param isDelete 是否删除缓存
     */
    public void refreshOnMsg(String key, boolean isDelete) {
        checkRegister();
        if (!useRedisAsCache || redissonClient == null || redissonClient.isShutdown()) {
            log.info("not use redis, just invalidate local cache key:{}", key);
            // 本地缓存失效
            invalidate(key);
            return;
        }
        log.info("refresh key:{}", key);
        // set redis
        String redisKey = getRedisKey(key);
        String lockKey = getLockKey(key);
        RBucket<V> bucket = redissonClient.getBucket(redisKey);
        // get the lock and refresh the redis cache
        RLock lock = redissonClient.getLock(lockKey);
        // 尝试获取锁，获取不到锁，直接删除本地缓存
        boolean success = lock.tryLock();
        // 获取分布式锁成功的刷新 redis，并使本地缓存失效
        if (success) {
            try {
                log.info("refreshOnMsg get lock key:{}", lockKey);
                // 先删掉 redis 中缓存 不然会导致本地缓存失效后，又把redis 中旧数据设置到本地缓存
                bucket.delete();
                if (isDelete) {
                    log.info("refreshOnMsg delete cache from redis key:{}", key);
                    invalidate(key);
                    return;
                }
                V value = getValueByFetcherOrExpression(key);

                if (value != null) {
                    // 设置到redis
                    log.info("refreshOnMsg set cache to redis key:{} value:{}", key, value);
                    // Redis 缓存时间是本地缓存的两倍 以防止本地缓存过期后，数据还没有更新到Redis
                    bucket.set(value, Duration.ofMillis(expireAfterWrite));
                } else {
                    // 防止缓存穿透 设置极短的过期时间
                    bucket.set(null, Duration.ofMillis(50));
                }
            } finally {
                lock.unlock();
            }
        } else {
            log.info("refreshOnMsg but get lock failed key:{} , just invalidate local cache", key);
        }
        // 获没获取到锁，都删除本地缓存
        invalidate(key);
    }

    private V getValueByFetcherOrExpression(String key) {
        // 获取数据超时时间
        final long timeout = fetchDataTimeout == WAIT_FOREVER ? MAX_FETCH_DATA_TIMEOUT : fetchDataTimeout;
        // 从数据获取器获取
        CompletableFuture<V> future;
        if (!fetchDataByExpression) {

            // 利用 CompletableFuture 实现超时获取
            future = CompletableFuture.supplyAsync(() -> {
                try {
                    return fetcher.apply(key);
                } catch (Exception e) {
                    log.error("get data from fetcher error, key:{}", key, e);
                    return null;
                }
            });
        } else {
            // 限制表达式执行时间
            // 执行表达式并转换结果为字符串
            future = CompletableFuture.supplyAsync(() -> {
                try {
                    return (V) compiledExp.execute(Collections.singletonMap("key", key));
                } catch (Exception e) {
                    log.error("execute expression error, key:{}", key, e);
                    return null;
                }
            });
        }
        return getV(key, future, timeout);
    }

    private static <V> V getV(String key, CompletableFuture<V> future, long timeout) {
        if (future == null) {
            return null;
        }
        V value = null;
        try {
            value = future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("get data from fetcher timeout, key:{}", key, e);
        } catch (InterruptedException e) {
            log.error("get data from fetcher interrupted, key:{}", key, e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.error("get data from fetcher error, key:{}", key, e);
        }
        return value;
    }

    /**
     * 从redis获取缓存数据或者从数据获取器获取
     *
     * @param key 缓存key
     * @return 缓存数据
     */
    private V loadCache(String key) {
        // 本地缓存没有，从redis获取
        log.info("getFromRedisOrFetcher key:{}", key);
        if (!useRedisAsCache || redissonClient == null || redissonClient.isShutdown()) {
            log.info("not use redis, get from fetcher or expression. key:{}", key);

            return getValueByFetcherOrExpression(key);

        }

        String redisKey = getRedisKey(key);
        RBucket<V> bucket = redissonClient.getBucket(redisKey);
        V value = bucket.get();
        // redis有，直接返回
        if (value != null) {
            log.info("get cache from redis key:{} value:{}", key, value);
            return value;
        }

        // redis也没有，从数据获取器获取 但是需要用分布式锁，防止缓存击穿
        final String lockKey = getLockKey(key);
        RLock lock = redissonClient.getLock(lockKey);
        // 获取分布式锁
        /*
         * 如果此时获取不到锁，说明有其它线程正在更新Redis数据，此时：
         * 1. 如果设置了超时时间,在超时时间内等待锁，如果超时直接返回null
         * 2、如果没有设置超时时间，直接阻塞等待锁
         */
        boolean locked = false;
        if (this.loadCacheWaitLockTimeout == WAIT_FOREVER) {
            // 不限制超时时间 使用 lock() 进行阻塞
            lock.lock();
            locked = true;
        } else {
            try {
                locked = lock.tryLock(this.loadCacheWaitLockTimeout, TimeUnit.MILLISECONDS);
                if (!locked) {
                    // 获取锁超时 直接返回null
                    log.warn("get lock timeout, key:{}", key);
                    return null;
                }
            } catch (InterruptedException ie) {
                log.error("get lock error, redis key:{}, lock key:{}", redisKey, lockKey, ie);
                Thread.currentThread().interrupt();
            }
        }
        try {

            return getValueFromDataFetcher(key, lockKey, bucket);

        } finally {
            if (locked) {
                lock.unlock();
            }
        }

    }

    @Nullable
    private V getValueFromDataFetcher(String key, String lockKey, RBucket<V> bucket) {
        V value;
        log.info("getFromRedisOrFetcher get lock key:{}", lockKey);
        // 取到锁，依旧需要再次判断 可能其它线程已经更新了Redis数据
        value = bucket.get();
        if (value != null) {
            log.info("get cache from redis again. redis key:{} value:{}", key, value);
            return value;
        }

        // 从数据获取器获取
        try {
            if (!this.fetchDataByExpression) {
                value = fetcher.apply(key);
            } else {
                // Execute expression and convert result to string
                Object result = compiledExp.execute(Collections.singletonMap("key", key));
                if (result != null) {
                    value = (V) result;
                }
            }
        } catch (Exception e) {
            log.error("fetcher error", e);
        }
        if (value != null) {
            // 设置到redis
            log.info("set cache to redis key:{} value:{}", key, value);
            // Redis 缓存时间是本地缓存的两倍 以防止本地缓存过期后，数据还没有更新到Redis
            bucket.set(value, Duration.ofMillis(expireAfterWrite));
        } else {
            // 防止缓存穿透 设置极短的过期时间
            bucket.set(null, Duration.ofMillis(50));
        }
        return value;
    }

    private String getRedisKey(String key) {
        return "CACHE:" + cacheName + ":" + key;
    }

    private String getLockKey(String key) {
        return "REFRESH_LOCK:" + cacheName + ":" + key;
    }

    /**
     * 消息队列类型
     */
    public enum MQType {
        /**
         * 不使用消息队列
         */
        NO_MQ,
        /**
         * 使用Redis消息队列
         */
        REDIS,
        /**
         * 使用RocketMQ消息队列
         */
        ROCKETMQ
    }

    // builder 模式
    public static class CacheBuilder<T> {
        private String cacheName;
        private int initialCapacity = 50;
        private long maximumSize = 50000L;
        private long expireAfterWrite = 5000L;
        // 默认一天刷新一次
        private long refreshAfterWrite = 86400000L;
        // -1 表示不限制超时时间
        private long loadCacheWaitLockTimeout = 3000L;
        // 获取数据超时时间
        private long fetchDataTimeout = DEFAULT_FETCH_DATA_TIMEOUT;
        private MQType mqType = MQType.ROCKETMQ;
        private ExecutorService executorService = new ThreadPoolExecutor(
                4,
                10,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(128),
                new ThreadFactoryBuilder().setNameFormat("cache-async-task-thread-%d").build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        private Function<String, T> fetcher;
        private String mqTopic;
        private String loadCacheExpression;

        public CacheBuilder<T> cacheName(String cacheName) {
            if (StringUtils.isBlank(cacheName)) {
                throw new IllegalArgumentException("cacheName can't be blank");
            }
            this.cacheName = cacheName;
            return this;
        }

        public CacheBuilder<T> initialCapacity(int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        public CacheBuilder<T> maximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
            return this;
        }

        public CacheBuilder<T> expireAfterWrite(long expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
            return this;
        }

        public CacheBuilder<T> refreshAfterWrite(long refreshAfterWrite) {
            this.refreshAfterWrite = refreshAfterWrite;
            return this;
        }

        public CacheBuilder<T> loadCacheWaitLockTimeout(long loadCacheWaitLockTimeout) {
            this.loadCacheWaitLockTimeout = loadCacheWaitLockTimeout;
            return this;
        }

        public CacheBuilder<T> fetchDataTimeout(long fetchDataTimeout) {
            this.fetchDataTimeout = fetchDataTimeout;
            return this;
        }

        public CacheBuilder<T> mqType(MQType mqType) {
            this.mqType = mqType;
            return this;
        }

        public CacheBuilder<T> executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public CacheBuilder<T> fetcher(Function<String, T> fetcher) {
            if (fetcher == null) {
                throw new IllegalArgumentException("fetcher or loadCacheExpression can't be null");
            }
            this.fetcher = fetcher;
            return this;
        }

        public CacheBuilder<T> loadCacheExpression(String loadCacheExpression) {
            if (StringUtils.isBlank(loadCacheExpression)) {
                throw new IllegalArgumentException("loadCacheExpression can't be blank");
            }
            this.loadCacheExpression = loadCacheExpression;
            return this;
        }

        public CacheBuilder<T> mqTopic(String mqTopic) {
            this.mqTopic = mqTopic;
            return this;
        }

        /**
         * 构建并注册缓存
         */
        public void buildAndRegister() {
            LightCache<T> lightCache = build();
            LightCacheManager.getInstance().registerCache(lightCache);
        }

        /**
         * 构建缓存
         *
         * @return 缓存
         */
        private LightCache<T> build() {
            return new LightCache<>(this);
        }
    }

    private LightCache(CacheBuilder<V> cacheBuilder) {
        if (StringUtils.isBlank(cacheBuilder.cacheName)) {
            throw new IllegalArgumentException("cacheName can't be blank");
        }
        if (StringUtils.isBlank(cacheBuilder.loadCacheExpression) && cacheBuilder.fetcher == null) {
            throw new IllegalArgumentException("fetcher or loadCacheExpression can't be null");
        }
        if (cacheBuilder.expireAfterWrite < 0) {
            throw new IllegalArgumentException("expireAfterWrite can't be less than 0");
        }
        if (cacheBuilder.refreshAfterWrite < 0) {
            throw new IllegalArgumentException("refreshAfterWrite can't be less than 0");
        }

        if (LightCacheManager.getInstance().isCacheExist(cacheBuilder.cacheName)) {
            throw new CacheNameExistException(cacheBuilder.cacheName);
        }
        this.cacheName = cacheBuilder.cacheName;
        int initialCapacity = cacheBuilder.initialCapacity;
        long maximumSize = cacheBuilder.maximumSize;
        this.expireAfterWrite = cacheBuilder.expireAfterWrite;
        long refreshAfterWrite = cacheBuilder.refreshAfterWrite;
        // -1 表示 不限制超时时间
        if (cacheBuilder.loadCacheWaitLockTimeout != WAIT_FOREVER
                && (cacheBuilder.loadCacheWaitLockTimeout < 0
                || cacheBuilder.loadCacheWaitLockTimeout > MAX_FETCH_DATA_TIMEOUT)) {
            log.warn("loadCacheWaitLockTimeout not set or invalid, set to {}ms", DEFAULT_FETCH_DATA_TIMEOUT);
            cacheBuilder.loadCacheWaitLockTimeout = DEFAULT_FETCH_DATA_TIMEOUT;
        }
        this.loadCacheWaitLockTimeout = cacheBuilder.loadCacheWaitLockTimeout;

        if (cacheBuilder.fetchDataTimeout != WAIT_FOREVER
                && (cacheBuilder.fetchDataTimeout < 0 || cacheBuilder.fetchDataTimeout > MAX_FETCH_DATA_TIMEOUT)) {
            log.warn("fetchDataTimeout not set or invalid, set to {}ms", DEFAULT_FETCH_DATA_TIMEOUT);
            cacheBuilder.fetchDataTimeout = DEFAULT_FETCH_DATA_TIMEOUT;
        }
        this.fetchDataTimeout = cacheBuilder.fetchDataTimeout;

        this.mqType = cacheBuilder.mqType;
        this.dataChangeTopic = cacheBuilder.mqTopic;
        ExecutorService executorService = cacheBuilder.executorService;

        if (StringUtils.isNotBlank(cacheBuilder.loadCacheExpression)) {
            this.fetchDataByExpression = true;
            // 编译表达式 并缓存
            this.compiledExp = AviatorEvaluator.compile(cacheBuilder.loadCacheExpression, true);
        } else {
            // 使用数据获取器
            this.fetcher = cacheBuilder.fetcher;
        }

        LightCacheManager cacheManager = LightCacheManager.getInstance();
        this.useRedisAsCache = cacheManager.isUseRedisAsCache();
        this.redissonClient = cacheManager.getRedissonClient();

        this.caffeine = Caffeine.newBuilder()
                .initialCapacity(initialCapacity)
                .maximumSize(maximumSize)
                .expireAfterWrite(Duration.ofMillis(expireAfterWrite))
                .refreshAfterWrite(Duration.ofMillis(refreshAfterWrite))
                .executor(executorService)
                // 缓存加载器
                // 以下几种情况会触发加载器加载数据
                // 1. 缓存不存在 2. 缓存过期 3. 缓存刷新
                .build(this::loadCache);

        log.info("LightCache build success, cacheName:{}", cacheName);
    }

    /**
     * 检查缓存是否注册
     */
    private void checkRegister() {
        // 如果没有注册缓存，不允许获取缓存
        if (!LightCacheManager.isInit() || !LightCacheManager.getInstance().isCacheExist(cacheName)) {
            throw new IllegalStateException(
                    "Cache not found:[" + cacheName + "]. You must register the cache before using it");
        }
    }

}