package com.oneinstep.light.cache.core.event;

import com.oneinstep.light.cache.core.exception.LightCacheException;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;

import java.nio.charset.StandardCharsets;

/**
 * RocketMQ 数据变更消费者
 * 默认通知方式
 */
@Slf4j
public class RocketMQDataChangeConsumer extends AbsDataChangeConsumer {

    /**
     * rocketmq 消费者
     */
    private final DefaultMQPushConsumer consumer;

    public RocketMQDataChangeConsumer(String namesrvAddr, String topic, String consumerGroup) {
        super(topic);
        log.info("RocketMQDataChangeConsumer init, namesrvAddr: {}, topic: {}, consumerGroup: {}", namesrvAddr, topic,
                consumerGroup);
        this.consumer = new DefaultMQPushConsumer(consumerGroup);
        this.consumer.setNamesrvAddr(namesrvAddr);
        // consumer启动后，从最后一次消费的位置开始消费，因为以前的消息对于本节点来说已经过期了，没有价值了，只关心最新的消息
        this.consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        // 广播模式，一个消息会被多个消费者消费
        this.consumer.setMessageModel(MessageModel.BROADCASTING);
        try {
            // 订阅主题，*表示订阅所有消息
            this.consumer.subscribe(topic, "*");
            // 注册消息监听器 并发消费
            this.consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
                try {
                    for (MessageExt msg : messages) {

                        byte[] body = msg.getBody();
                        if (body == null) {
                            log.error("Message body is null, messageId: {}", msg.getMsgId());
                            // 消息体为空是不可恢复的错误，直接返回消费成功
                            continue;
                        }
                        String message = new String(body, StandardCharsets.UTF_8);
                        consumeMsg(message);

                    }
                    // 所有消息都已处理，返回消费成功
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                } catch (Exception e) {
                    // 批量消息处理过程中发生系统级异常，需要重试
                    log.error("System error while consuming messages", e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            });
        } catch (MQClientException e) {
            // 消费者初始化失败是致命错误，需要抛出异常中断启动
            log.error("RocketMQDataChangeConsumer init failed, namesrvAddr: {}, topic: {}, consumerGroup: {}",
                    namesrvAddr, topic, consumerGroup, e);
            throw new LightCacheException("Fail to init RocketMQDataChangeConsumer with topic: " + topic, e);
        }
    }

    @Override
    public void start() {
        try {
            this.consumer.start();
            log.info("RocketMQDataChangeConsumer started");
        } catch (MQClientException e) {
            log.error("RocketMQDataChangeConsumer start failed, topic: {}, consumerGroup: {}", this.topic,
                    this.consumer.getConsumerGroup(), e);
            throw new LightCacheException("Fail to start RocketMQDataChangeConsumer with topic: " + topic, e);
        }
    }

    @Override
    public void stop() {
        this.isRunning = false;
        if (this.consumer != null) {
            this.consumer.shutdown();
            log.info("RocketMQDataChangeConsumer stopped");
        }
    }

}
