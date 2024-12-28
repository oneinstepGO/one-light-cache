package com.oneinstep.light.cache.core.event;

import com.oneinstep.light.cache.core.exception.LightCacheException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kafka 数据变更消费者
 */
@Slf4j
public class KafkaDataChangeConsumer extends AbsDataChangeConsumer {

    private final KafkaConsumer<String, String> consumer;
    private final ExecutorService executorService;

    public KafkaDataChangeConsumer(String bootstrapServers, String topic, String consumerGroup) {
        super(topic);
        log.info("KafkaDataChangeConsumer init, bootstrapServers: {}, topic: {}, consumerGroup: {}",
                bootstrapServers, topic, consumerGroup);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // 不自动提交偏移量
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        // 从最新的消息开始消费
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        // 设置客户端ID
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "kafka-consumer-" + topic);
        // 设置消费者组ID
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup + "-" + topic);

        try {
            this.consumer = new KafkaConsumer<>(props);
            this.executorService = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r);
                thread.setName("kafka-consumer-" + topic);
                return thread;
            });
        } catch (Exception e) {
            log.error("KafkaDataChangeConsumer init failed, bootstrapServers: {}, topic: {}, consumerGroup: {}",
                    bootstrapServers, topic, consumerGroup, e);
            throw new LightCacheException("Failed to init KafkaDataChangeConsumer with topic: " + topic, e);
        }
    }

    @Override
    public void start() {
        try {
            // 订阅主题
            consumer.subscribe(Collections.singletonList(topic));
            log.info("KafkaDataChangeConsumer subscribed to topic: {}", topic);

            // 在单独的线程中启动消费
            executorService.submit(() -> {
                while (isRunning) {
                    try {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                        for (ConsumerRecord<String, String> msg : records) {
                            log.info("Received message: topic={}, partition={}, offset={}, key={}, value={}",
                                    msg.topic(), msg.partition(), msg.offset(), msg.key(), msg.value());
                            consumeMsg(msg.value());
                            log.info("Successfully processed message: {}", msg.value());
                        }

                        // 所有消息都已处理，提交消费位移
                        consumer.commitSync();

                    } catch (Exception e) {
                        if (isRunning) {
                            log.error("Error while consuming messages from topic: {}", topic, e);
                            try {
                                // Add a small delay before retrying
                                Thread.sleep(1000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
            });

            log.info("KafkaDataChangeConsumer started for topic: {}", topic);
        } catch (Exception e) {
            log.error("KafkaDataChangeConsumer start failed, topic: {}", topic, e);
            throw new LightCacheException("Failed to start KafkaDataChangeConsumer with topic: " + topic, e);
        }
    }

    @Override
    public void stop() {
        this.isRunning = false;
        if (this.consumer != null) {
            try {
                this.consumer.close();
                log.info("Kafka consumer closed for topic: {}", topic);
            } catch (Exception e) {
                log.error("Error closing Kafka consumer for topic: {}", topic, e);
            }
        }
        if (this.executorService != null) {
            this.executorService.shutdown();
            log.info("Kafka consumer executor service shutdown for topic: {}", topic);
        }
        log.info("KafkaDataChangeConsumer stopped for topic: {}", topic);
    }

}