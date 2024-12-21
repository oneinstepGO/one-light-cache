package com.oneinstep.light.cache.demo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.demo.facade.UserDTO;
import com.oneinstep.light.cache.starter.configuration.LightCacheManagerProperties;

import jakarta.annotation.Resource;

@SpringBootTest(classes = Application.class)
class LightCacheSpringBootTest {

    @Autowired
    private LightCacheManager lightCacheManager;

    @Resource
    private LightCacheManagerProperties lightCacheManagerProperties;

    @BeforeEach
    public void setUp() {
        // 输出 lightCacheConfig
        System.out.println(lightCacheManagerProperties);
    }

    @Test
    void testCreateCacheWithSpringBoot() {
        LightCache<UserDTO> cache = lightCacheManager.getCache("test-user");
        Assertions.assertNotNull(cache);
        UserDTO userDTO = cache.get("1");
        Assertions.assertNotNull(userDTO);
        Assertions.assertEquals("test-1", userDTO.getUserName());

        // 测试数据变更
        userDTO = cache.get("2");
        Assertions.assertNotNull(userDTO);
        Assertions.assertEquals("test-2", userDTO.getUserName());

        // 等待 7 秒，等待缓存过期
        try {
            Thread.sleep(7000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        userDTO = cache.get("2");
        Assertions.assertNotNull(userDTO);
        Assertions.assertEquals("test-2", userDTO.getUserName());
    }


}
