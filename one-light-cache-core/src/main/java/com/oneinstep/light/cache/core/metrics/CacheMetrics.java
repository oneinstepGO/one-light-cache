package com.oneinstep.light.cache.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存指标
 */
public class CacheMetrics {

    /**
     * 缓存加载异常计数器
     */
    private final Counter cacheLoadExceptionCounter;

    /**
     * 缓存加载耗时计时器
     */
    private final Timer cacheLoadTimer;

    /**
     * 缓存大小指标
     */
    private final AtomicLong currentSize;

    /**
     * 缓存命中率
     */
    private final AtomicLong hitCount;

    /**
     * 缓存名称标签
     */
    private static final String CACHE_NAME_TAG = "cacheName";

    /**
     * 构造函数
     *
     * @param registry  指标注册器
     * @param cacheName 缓存名称
     */
    public CacheMetrics(MeterRegistry registry, String cacheName) {
        this.cacheLoadExceptionCounter = registry.counter(CacheMetricsNameConstants.CACHE_LOAD_ERRORS, CACHE_NAME_TAG,
                cacheName);
        this.cacheLoadTimer = registry.timer(CacheMetricsNameConstants.CACHE_LOAD_TIME, CACHE_NAME_TAG, cacheName);
        io.micrometer.core.instrument.Gauge
                .builder(CacheMetricsNameConstants.CACHE_SIZE, this, CacheMetrics::getCurrentSize)
                .tag(CACHE_NAME_TAG, cacheName)
                .register(registry);
        io.micrometer.core.instrument.Gauge
                .builder(CacheMetricsNameConstants.CACHE_HIT_COUNT, this, CacheMetrics::getHitCount)
                .tag(CACHE_NAME_TAG, cacheName)
                .register(registry);
        io.micrometer.core.instrument.Gauge
                .builder(CacheMetricsNameConstants.CACHE_HIT_RATE, this, CacheMetrics::getHitRate)
                .tag(CACHE_NAME_TAG, cacheName)
                .register(registry);
        this.currentSize = new AtomicLong(0);
        this.hitCount = new AtomicLong(0);
    }

    /**
     * 记录缓存加载异常
     */
    public void recordLoadError(String cacheName) {
        cacheLoadExceptionCounter.increment();
    }

    /**
     * 开始缓存加载计时
     *
     * @return 计时器样本
     */
    public Timer.Sample startTimer() {
        return Timer.start();
    }

    /**
     * 停止缓存加载计时
     *
     * @param sample 计时器样本
     */
    public void stopTimer(Timer.Sample sample) {
        if (cacheLoadTimer != null) {
            sample.stop(cacheLoadTimer);
        }
    }

    /**
     * 更新缓存大小
     *
     * @param size 当前缓存大小
     */
    public void updateCacheSize(long size) {
        this.currentSize.set(size);
    }

    /**
     * 获取当前缓存大小
     */
    public long getCurrentSize() {
        return currentSize.get();
    }

    /**
     * 获取当前缓存大小
     */
    public long getCacheSize() {
        return currentSize.get();
    }

    /**
     * 增加缓存大小
     *
     * @param size 增加的大小
     */
    public void addCacheSize(long size) {
        this.currentSize.addAndGet(size);
    }

    /**
     * 减少缓存大小
     *
     * @param size 减少的大小
     */
    public void subCacheSize(long size) {
        this.currentSize.addAndGet(-size);
    }

    /**
     * 增加缓存命中次数
     *
     * @param hit 命中次数
     */
    public void addHitCount(long hit) {
        this.hitCount.addAndGet(hit);
    }

    /**
     * 获取缓存命中次数
     */
    public long getHitCount() {
        return hitCount.get();
    }

    /**
     * 获取缓存命中率
     */
    public double getHitRate() {
        return (double) hitCount.get() / currentSize.get();
    }

}
