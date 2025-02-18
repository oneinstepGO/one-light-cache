package com.oneinstep.light.cache.core;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CacheDelayMsg {
    private String cacheName;
    private String cacheKey;
}
