package com.oneinstep.light.cache.demo;

import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.core.serializer.KryoSerializer;
import com.oneinstep.light.cache.demo.facade.UserDTO;
import com.oneinstep.light.cache.demo.facade.UserFacade;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheEventTest extends BaseTest {

    @Resource
    private UserFacade userFacade;

    @Test
    void testCacheEvents() {
        // 创建一个测试监听器
        TestCacheEventListener listener = new TestCacheEventListener();

        // 注册带监听器的缓存
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName("user-cache-test")
                .addEventListeners(listener)
                .fetcher(this::getUser)
                // .serializer(new ProtobufSerializer())
                .serializer(new KryoSerializer())
                .buildAndRegister();

        LightCache<UserDTO> cache = LightCacheManager.getInstance().getCache("user-cache-test");

        // 测试put事件
        String userId = "1";
        cache.get(userId);
        assertTrue(listener.isPutEventFired());

        // 测试remove事件
        cache.invalidate(userId);
        assertTrue(listener.isRemoveEventFired());
    }


}
