package com.oneinstep.light.cache.demo.runner;

import static com.oneinstep.light.cache.demo.constant.DataNameConstant.USER;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.demo.bean.User;
import com.oneinstep.light.cache.starter.producer.IDataChangeMsgProducer;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;


/**
 * 测试场景
 * 多个节点，某一时刻，一个节点修改了数据，其他节点如何感知并更新缓存
 * 与此同时，每个节点都有大量的请求在不停获取缓存
 * <p>
 * 希望保证如下功能：
 * 1、同一时刻，只有一个节点请求数据库并更新redis缓存                       ｜  ==> 满足
 * 2、其他节点在获取缓存时，如果发现缓存过期，应该阻塞等待，直到缓存更新完成     ｜
 * 3、如果缓存更新失败，应该重试
 */
// @Component
@Slf4j
public class TestRunner4 implements CommandLineRunner {

    @Value("${update.value:false}")
    private boolean updateValue;

    @Resource(name = "rocketMQDataChangeMsgProducer")
    private IDataChangeMsgProducer mqProducer;
    @Resource
    private LightCacheManager lightCacheManager;

    @Override
    public void run(String... args) throws Exception {
        Random r1 = new Random();
        LightCacheManager.<User>newCacheBuilder()
                .cacheName(USER)
                .initialCapacity(20)
                .maximumSize(100)
                .expireAfterWrite(5000)
                // 设置缓存加载超时时间 这个根据实际情况设置 超过这个时间还未获取到锁可能会返回null
                // 也可以设置为-1 表示一直等待 直到获取到锁，0表示不等待
                .loadCacheWaitLockTimeout(1000)
                .fetchDataTimeout(3000)
                .fetcher(userIdStr -> {
                    log.info("Fetching data from db, userId: {}", userIdStr);
                    try {
                        TimeUnit.MILLISECONDS.sleep(300 + r1.nextInt(50));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    User user = new User();
                    user.setUserId(Long.parseLong(userIdStr));
                    user.setUserName("user-" + userIdStr + "-" + System.currentTimeMillis());
                    return user;
                })
                .mqTopic("user_data_change")
                .buildAndRegister();

        // 开启三个线程不停获取缓存
        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(200);
                        long userId = 1L;
                        long start = System.currentTimeMillis();
                        lightCacheManager.getCache(USER).get(String.valueOf(userId));
                        long cost = System.currentTimeMillis() - start;
                        if (cost > 100) {
                            log.warn("Get data cost too much time: {}ms", cost);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        if (!updateValue) {
            return;
        }
        // 另一个线程间隔15秒修改一次数据 发送消息
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(15000);

                    // 设置完成后，使用 rocketmq 发送消息
                    try {

                        JSONObject dataChangeMsg = new JSONObject();
                        dataChangeMsg.put("dataName", USER);
                        dataChangeMsg.put("dataId", 1L);
                        dataChangeMsg.put("type", "UPDATE");

                        if (mqProducer == null) {
                            log.error("Failed to get mqProducer");
                            return;
                        }
                        mqProducer.sendMsg("user_data_change", JSON.toJSONString(dataChangeMsg));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

}
