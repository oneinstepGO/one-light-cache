package com.oneinstep.light.cache.demo;

import com.alibaba.fastjson2.JSON;
import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.core.event.ConsumerCreateEvent;
import com.oneinstep.light.cache.core.event.DataChangeMsg;
import com.oneinstep.light.cache.demo.facade.UserDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

@SpringBootTest(classes = Application.class)
class KafkaCacheTest {

    @Autowired
    private LightCacheManager cacheManager;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    private final AtomicInteger cacheIndex = new AtomicInteger(0);

    private static final String CACHE_NAME = "test_user_cache_";

    private static final Logger log = LoggerFactory.getLogger(KafkaCacheTest.class);
    private KafkaProducer<String, String> producer;
    private AdminClient adminClient;
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        producer = new KafkaProducer<>(props);
        adminClient = AdminClient.create(props);
    }

    private void createTopicIfNotExists(String topic) {
        try {
            NewTopic newTopic = new NewTopic(topic, 1, (short) 1);

            // 如果主题不存在，则创建主题
            if (!adminClient.listTopics().names().get().contains(topic)) {
                adminClient.createTopics(Collections.singleton(newTopic))
                        .all()
                        .get(10, TimeUnit.SECONDS);
                log.info("Created Kafka topic: {}", topic);
            } else {
                log.info("Kafka topic already exists: {}", topic);
            }
            // Wait a bit for the topic to be fully created
            Thread.sleep(1000);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.warn("Error creating topic {}: {}", topic, e.getMessage());
        }
    }

    @Test
    void testKafkaCacheDelete() throws InterruptedException, ExecutionException, TimeoutException {
        int index = getIndex();
        String topic = "test_user_topic_" + index;

        // Create topic before using it
        createTopicIfNotExists(topic);

        // Register cache with Kafka as message queue
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName(CACHE_NAME + index)
                .initialCapacity(10)
                .maximumSize(100)
                .expireAfterWrite(5000)
                .fetcher(this::getUser)
                .mqTopic(topic)
                .mqType(LightCache.MQType.KAFKA)
                .buildAndRegister();

        // Publish consumer create event
        eventPublisher.publishEvent(new ConsumerCreateEvent(this, CACHE_NAME + index, topic, LightCache.MQType.KAFKA));

        // Wait for the consumer to start
        Thread.sleep(2000);

        LightCache<UserDTO> cache = cacheManager.getCache(CACHE_NAME + index);

        // First access
        String userId = "1";
        UserDTO user1 = cache.get(userId);
        log.info("First access - user1: {}", user1);
        assertNotNull(user1);

        // Send delete message through Kafka
        DataChangeMsg msg = DataChangeMsg.builder()
                .dataName(CACHE_NAME + index)
                .dataId(userId)
                .type(DataChangeMsg.DataChangeType.DELETE)
                .build();
        log.info("Sending delete message: {}", msg);
        producer.send(new ProducerRecord<>(topic, JSON.toJSONString(msg))).get(5, TimeUnit.SECONDS);
        producer.flush();

        // Wait for message processing
        Thread.sleep(5000);

        // Get data again, should be different instance
        UserDTO user2 = cache.get(userId);
        log.info("Second access - user2: {}", user2);
        assertNotNull(user2);
        assertNotSame(user1, user2, "Should get a new instance after delete");
    }

    public int getIndex() {
        return cacheIndex.getAndIncrement();
    }

    public UserDTO getUser(String userId) {
        UserDTO user = UserDTO.builder()
                .userId(userId)
                .userName("user_" + userId)
                .createTime(LocalDateTime.now())
                .build();
        log.info("Generated new user: {}", user);
        return user;
    }
}
