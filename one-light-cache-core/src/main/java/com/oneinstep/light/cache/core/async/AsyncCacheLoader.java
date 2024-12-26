package com.oneinstep.light.cache.core.async;

import cn.hutool.core.thread.NamedThreadFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 异步缓存加载器
 * <p>
 * AsyncCacheLoader应该在:
 * 预加载/预热缓存时使用
 * 批量加载缓存时使用
 * 需要异步更新缓存时使用
 * 示例代码：
 *
 * <pre>
 * &#64;Service
 * public class UserService {
 *     &#64;Resource
 *     private AsyncCacheLoader<User> asyncCacheLoader;
 *     &#64;Resource
 *     private LightCache<User> userCache;
 *
 *     // 预热缓存
 *     public void warmupCache(List<String> userIds) {
 *         List<CompletableFuture<User>> futures = userIds.stream()
 *                 .map(id -> asyncCacheLoader.asyncLoad(id, this::loadUser))
 *                 .collect(Collectors.toList());
 *
 *         CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
 *                 .thenAccept(v -> log.info("Cache warmup completed"));
 *     }
 *
 *     // 异步更新缓存
 *     public void asyncUpdateCache(String userId) {
 *         asyncCacheLoader.asyncLoad(userId, this::loadUser)
 *                 .thenAccept(user -> userCache.put(userId, user));
 *     }
 *
 *     private User loadUser(String userId) {
 *         // 从数据源加载用户
 *         return userRepository.findById(userId);
 *     }
 * }
 * </pre>
 */
public class AsyncCacheLoader {

    private AsyncCacheLoader() {
    }

    private static final ExecutorService EXECUTOR_SERVICE =
            Executors.newFixedThreadPool(10, new NamedThreadFactory("AsyncCacheLoader", true));

    /**
     * 异步加载缓存
     * 该类用于异步加载缓存数据,避免同步加载缓存时阻塞主线程。
     * 通过传入缓存key和加载器函数,在独立的线程池中异步执行加载操作,
     * 并返回CompletableFuture对象,调用方可以通过该对象获取异步加载的结果。
     *
     * @param key    缓存key
     * @param loader 缓存加载器函数,用于实际加载缓存数据
     * @return CompletableFuture对象, 包含异步加载的缓存值
     */
    public static <V> CompletableFuture<V> asyncLoad(String key, Function<String, V> loader) {
        return CompletableFuture.supplyAsync(() -> loader.apply(key), EXECUTOR_SERVICE);
    }

    /**
     * 异步加载缓存
     * 该类用于异步加载缓存数据,避免同步加载缓存时阻塞主线程。
     * 通过传入缓存key和加载器函数,在独立的线程池中异步执行加载操作,
     * 并返回CompletableFuture对象,调用方可以通过该对象获取异步加载的结果。
     *
     * @param loader 缓存加载器函数,用于实际加载缓存数据
     * @return CompletableFuture对象, 包含异步加载的缓存值
     */
    public static <V> CompletableFuture<V> asyncLoad(Supplier<V> loader) {
        return CompletableFuture.supplyAsync(loader, EXECUTOR_SERVICE);
    }

}