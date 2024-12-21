package com.oneinstep.light.cache.starter.producer;

/**
 * 数据变更消息生产者
 */
public interface IDataChangeMsgProducer {

    /**
     * 发送消息
     *
     * @param topic   主题
     * @param message 消息
     */
    void sendMsg(String topic, String message);
}
