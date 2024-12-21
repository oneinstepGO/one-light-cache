package com.oneinstep.light.cache.starter.producer;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;

import com.oneinstep.light.cache.starter.configuration.LightCacheManagerProperties;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.remoting.exception.RemotingException;

/**
 * RocketMQ 数据变更消息生产者
 */
@Slf4j
public class RocketMQDataChangeMsgProducer implements IDataChangeMsgProducer {

    private DefaultMQProducer producer;

    private final LightCacheManagerProperties properties;

    public RocketMQDataChangeMsgProducer(LightCacheManagerProperties properties) {
        this.properties = properties;
    }

    public void init() {

        String rocketmqNameServer = properties.getRocketmqNameServer();

        if (rocketmqNameServer == null) {
            log.error("Failed to start producer, rocketmq.name-server is not configured");
            return;
        }
        String producerGroup = properties.getProducerGroup();

        this.producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(rocketmqNameServer);
        try {
            producer.start();
        } catch (MQClientException e) {
            log.error("Failed to start producer", e);
        }
    }

    public void destroy() {
        producer.shutdown();
    }

    /**
     * 发送消息
     *
     * @param topic 主题
     * @param tag   标签
     * @param msg   消息
     * @param keys  键
     */
    public void send(String topic, String tag, String msg, String keys) {
        Message message = new Message(topic, tag, msg.getBytes());
        if (keys != null) {
            message.setKeys(keys);
        }
        try {
            producer.send(message);
        } catch (MQClientException | RemotingException | MQBrokerException e) {
            log.error("send msg error. Topic:{}", topic, e);
        } catch (InterruptedException ie) {
            log.error("send msg error. Topic:{}", topic, ie);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void sendMsg(String topic, String message) {
        try {
            send(topic, null, message, null);
        } catch (Exception e) {
            log.error("Failed to send message", e);
        }
    }

}
