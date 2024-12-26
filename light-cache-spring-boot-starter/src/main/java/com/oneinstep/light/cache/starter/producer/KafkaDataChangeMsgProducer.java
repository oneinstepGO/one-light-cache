package com.oneinstep.light.cache.starter.producer;

import com.oneinstep.light.cache.starter.configuration.LightCacheManagerProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * Kafka 数据变更消息生产者
 */
@Slf4j
public class KafkaDataChangeMsgProducer implements IDataChangeMsgProducer {

    private KafkaProducer<String, String> producer;
    private final LightCacheManagerProperties properties;

    public KafkaDataChangeMsgProducer(LightCacheManagerProperties properties) {
        this.properties = properties;
    }

    public void init() {
        String bootstrapServers = properties.getKafkaBootstrapServers();
        if (bootstrapServers == null) {
            log.error("Failed to start producer, kafka.bootstrap.servers is not configured");
            return;
        }

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // 生产者确认机制
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        // 重试次数
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        // 批量发送大小
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        // 批量发送等待时间
        props.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        // 生产者缓冲区大小
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

        try {
            this.producer = new KafkaProducer<>(props);
        } catch (Exception e) {
            log.error("Failed to create Kafka producer", e);
        }
    }

    public void destroy() {
        if (producer != null) {
            producer.close();
        }
    }

    @Override
    public void sendMsg(String topic, String message) {
        try {
            if (producer != null) {
                producer.send(new ProducerRecord<>(topic, message));
            }
        } catch (Exception e) {
            log.error("Failed to send message to Kafka", e);
        }
    }
} 