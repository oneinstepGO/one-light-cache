package com.oneinstep.light.cache.demo;

import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.core.serializer.KryoSerializer;
import com.oneinstep.light.cache.core.serializer.ProtobufSerializer;
import com.oneinstep.light.cache.demo.facade.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class CacheSerializerTest extends BaseTest {

    @Test
    void testCacheWithSerializer() {
        // Create cache with Kryo serializer
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName("user-cache")
                .serializer(new KryoSerializer())
                .fetcher(this::getUser)
                .buildAndRegister();

        // Create cache with Protobuf serializer
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName("user-cache-proto")
                .serializer(new ProtobufSerializer())
                .fetcher(this::getUser)
                .buildAndRegister();

        LightCache<UserDTO> cache = LightCacheManager.getInstance().getCache("user-cache");
        UserDTO user = cache.get("1");
        assertNotNull(user);
    }

    @Test
    void testCacheWithMetrics() {
        // Create cache with stats enabled
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName("user-cache-metrics")
                .serializer(new KryoSerializer())
                .fetcher(this::getUser)
                .enableStats(true)  // Enable stats tracking
                .buildAndRegister();

        LightCache<UserDTO> cache = LightCacheManager.getInstance().getCache("user-cache-metrics");

        // First access - should be a miss
        cache.get("1");
        // Second access - should be a hit
        cache.get("1");

        assertNotNull(cache.stats());
        assertTrue(cache.stats().getHitCount() > 0);
    }
}
