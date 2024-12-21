package com.oneinstep.light.cache.core.exception;

public class CacheNameExistException extends LightCacheException {

    public CacheNameExistException(String cacheName) {
        super("CacheName: " + cacheName + " is Exist.");
    }
}
