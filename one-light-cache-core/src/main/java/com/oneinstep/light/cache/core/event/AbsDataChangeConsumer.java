package com.oneinstep.light.cache.core.event;

import com.alibaba.fastjson2.JSON;
import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 数据变更消费者抽象类
 */
@Slf4j
public abstract class AbsDataChangeConsumer {

    /**
     * 数据变更主题
     */
    protected final String topic;

    /**
     * 是否正在运行
     */
    protected volatile boolean isRunning = true;

    protected AbsDataChangeConsumer(final String topic) {
        this.topic = topic;
    }

    /**
     * 开启consumer
     */
    public abstract void start();

    /**
     * 关闭consumer
     */
    public abstract void stop();

    /**
     * 消费消息
     *
     * @param message 消息
     */
    public final void consumeMsg(String message) {
        try {
            if (!isRunning) {
                log.warn("Consumer is not running");
                return;
            }
            log.info("{} Received Topic[{}] message: {}", this.getClass().getSimpleName(), topic, message);
            if (StringUtils.isBlank(message) || "null".equalsIgnoreCase(message) || !JSON.isValid(message)) {
                log.warn("Invalid message: {}", message);
                return;
            }

            DataChangeMsg dataChangeMsg = JSON.parseObject(message, DataChangeMsg.class);

            if (dataChangeMsg == null) {
                log.warn("{} Failed to parse message: {}", this.getClass().getSimpleName(), message);
                return;
            }

            String dataName = dataChangeMsg.getDataName();
            String dataId = dataChangeMsg.getDataId();
            DataChangeMsg.DataChangeType type = dataChangeMsg.getType();
            if (StringUtils.isBlank(dataName) || StringUtils.isBlank(dataId) || type == null) {
                log.warn("Invalid data change message: {}", message);
                return;
            }

            // 获取缓存管理器
            LightCacheManager cacheManager = LightCacheManager.getInstance();

            // 检查缓存是否存在
            boolean cacheExist = cacheManager.isCacheExist(dataName);
            if (!cacheExist) {
                log.error("Cache is not exist: {}", dataName);
                return;
            }

            // 获取缓存
            LightCache<?> cache = cacheManager.getCache(dataName);

            switch (type) {
                case UPDATE:
                    cache.refreshOnMsg(dataId, false);
                    break;
                case DELETE:
                    cache.refreshOnMsg(dataId, true);
                    break;
                case ADD:
                    // 不需要处理
                    break;
                default:
                    cache.invalidate(dataId);
                    log.warn("Unsupported data change type: {}", type);
                    break;
            }
        } catch (Exception e) {
            log.error("Fail to consume message : {}", message, e);
        }
    }
}