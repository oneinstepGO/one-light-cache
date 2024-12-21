package com.oneinstep.light.cache.core.exception;

/**
 * Light-Cache 异常基类
 */
public class LightCacheException extends RuntimeException {

    public LightCacheException(String message) {
        super(message);
    }

    public LightCacheException(String message, Throwable cause) {
        super(message, cause);
    }

}
