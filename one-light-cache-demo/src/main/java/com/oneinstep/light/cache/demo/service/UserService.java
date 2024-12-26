package com.oneinstep.light.cache.demo.service;

import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.core.async.AsyncCacheLoader;
import com.oneinstep.light.cache.core.serializer.KryoSerializer;
import com.oneinstep.light.cache.demo.facade.UserDTO;
import com.oneinstep.light.cache.demo.facade.UserFacade;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 用户服务
 * 主要用于演示AsyncCacheLoader的用法
 */
@Service
@Slf4j
@ConditionalOnClass(LightCacheManager.class)
@DependsOn("lightCacheManager")
public class UserService {
    @Resource
    private UserFacade userFacade;

    private LightCache<UserDTO> userCache;

    @PostConstruct
    public void init() {

        LightCacheManager instance = LightCacheManager.getInstance();
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName("user-cache-warmup")
                .serializer(new KryoSerializer())
                .fetcher(this::getUser)
                .buildAndRegister();
        this.userCache = instance.getCache("user-cache-warmup");
    }

    private UserDTO getUser(String userId) {
        return UserDTO.builder().userId(userId).userName("user-" + userId)
                .createTime(LocalDateTime.now()).build();
    }

    // 预热缓存
    public void warmupCache(List<String> userIds) {
        List<CompletableFuture<UserDTO>> futures = userIds.stream()
                .map(id -> AsyncCacheLoader.asyncLoad(id, userFacade::getUser))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenAccept(v -> log.info("Cache warmup completed"));
    }

    // 异步更新缓存
    public void asyncUpdateCache(String userId) {
        AsyncCacheLoader.asyncLoad(userId, userFacade::getUser)
                .thenAccept(userDTO -> userCache.put(userId, userDTO));
    }

}
