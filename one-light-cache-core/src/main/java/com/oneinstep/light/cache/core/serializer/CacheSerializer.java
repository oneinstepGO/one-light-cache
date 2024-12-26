package com.oneinstep.light.cache.core.serializer;

/**
 * 缓存序列化器
 */
public interface CacheSerializer {
    /**
     * 序列化对象
     *
     * @param obj 对象
     * @return 序列化后的字节数组
     */
    byte[] serialize(Object obj);

    /**
     * 反序列化对象
     *
     * @param bytes 字节数组
     * @return 反序列化后的对象
     */
    Object deserialize(byte[] bytes);
}
