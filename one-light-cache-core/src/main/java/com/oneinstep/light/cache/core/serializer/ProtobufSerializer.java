package com.oneinstep.light.cache.core.serializer;

/**
 * Protobuf序列化器
 */
public class ProtobufSerializer implements CacheSerializer {

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        return obj.toString().getBytes();
    }

    @Override
    public Object deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return new String(bytes);
    }

}
