package com.oneinstep.light.cache.core.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import org.objenesis.strategy.StdInstantiatorStrategy;

/**
 * Kryo序列化器
 */
public class KryoSerializer implements CacheSerializer {

    private final Kryo kryo = new Kryo();

    public KryoSerializer() {
        // 注册常用类
        kryo.setRegistrationRequired(false); // 允许序列化未注册的类
        kryo.setReferences(true); // 支持对象引用
        // 使用 Objenesis 策略来创建对象，不需要无参构造函数
        kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
    }

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        Output output = new Output(1024, -1);
        kryo.writeClassAndObject(output, obj);
        return output.toBytes();
    }

    @Override
    public Object deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return kryo.readClassAndObject(new Input(bytes));
    }

}
