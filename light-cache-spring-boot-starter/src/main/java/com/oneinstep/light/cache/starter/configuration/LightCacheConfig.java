package com.oneinstep.light.cache.starter.configuration;

import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCache.MQType;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.core.exception.LightCacheException;
import com.oneinstep.light.cache.starter.configuration.LightCacheManagerProperties.LightCacheProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Slf4j
@Configuration
@Component
public class LightCacheConfig {

    @Resource
    private LightCacheManager manager;

    @Resource
    private LightCacheManagerProperties properties;

    @PostConstruct
    public void registerLightCache() {
        log.info("Start registering LightCache, configs: {}", properties.getCacheConfigs());
        if (properties.getCacheConfigs() == null || properties.getCacheConfigs().isEmpty()) {
            log.warn("No cache configs on properties or yaml file");
            return;
        }

        // 注册缓存
        for (LightCacheProperties cacheConfig : properties.getCacheConfigs()) {
            try {
                // 创建并注册缓存
                LightCache.createFromProperties(
                        cacheConfig.getCacheName(),
                        cacheConfig.getLoadCacheExpression(),
                        cacheConfig.getInitialCapacity(),
                        cacheConfig.getMaximumSize(),
                        cacheConfig.getExpireAfterWrite(),
                        cacheConfig.getRefreshAfterWrite(),
                        cacheConfig.getLoadCacheWaitLockTimeout(),
                        cacheConfig.getFetchDataTimeout(),
                        MQType.valueOf(StringUtils.upperCase(cacheConfig.getMqType())),
                        cacheConfig.getMqTopic());
                log.info("Successfully created and registered cache: {}", cacheConfig.getCacheName());
            } catch (Exception e) {
                log.error("Failed to create and register cache: {}", cacheConfig.getCacheName(), e);
                throw new LightCacheException("Failed to create and register cache: " + cacheConfig.getCacheName(), e);
            }
        }
    }

}