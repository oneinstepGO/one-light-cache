package com.oneinstep.light.cache.demo;

import com.oneinstep.light.cache.core.event.CacheEventListener;
import lombok.Getter;

@Getter
public class TestCacheEventListener implements CacheEventListener {
    private boolean putEventFired;
    private boolean removeEventFired;
    private boolean expireEventFired;

    @Override
    public void onPut(String cacheName, String key, Object value) {
        putEventFired = true;
    }

    @Override
    public void onRemove(String cacheName, String key) {
        removeEventFired = true;
    }

    @Override
    public void onExpire(String cacheName, String key) {
        expireEventFired = true;
    }
}
