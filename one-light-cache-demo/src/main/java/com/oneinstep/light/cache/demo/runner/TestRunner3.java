package com.oneinstep.light.cache.demo.runner;

import com.alibaba.fastjson2.JSON;
import com.oneinstep.light.cache.core.LightCache;
import com.oneinstep.light.cache.core.LightCacheManager;
import com.oneinstep.light.cache.core.event.DataChangeMsg;
import com.oneinstep.light.cache.demo.facade.UserDTO;
import com.oneinstep.light.cache.starter.producer.IDataChangeMsgProducer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.oneinstep.light.cache.demo.constant.DataNameConstant.USER;


//@Component
@Slf4j
public class TestRunner3 implements CommandLineRunner {

    @Resource(name = "redisDataChangeMsgProducer")
    private IDataChangeMsgProducer mqProducer;
    @Resource
    private LightCacheManager lightCacheManager;

    @Override
    public void run(String... args) throws Exception {
        Random r1 = new Random();
        LightCacheManager.<UserDTO>newCacheBuilder()
                .cacheName(USER)
                .initialCapacity(20)
                .maximumSize(100)
                .expireAfterWrite(5000)
                .fetcher(userIdStr -> {
                    log.info("Fetching data from db, userId: {}", userIdStr);
                    try {
                        TimeUnit.MILLISECONDS.sleep(300 + r1.nextInt(50));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    UserDTO user = UserDTO.builder()
                            .userId(userIdStr)
                            .userName("user-" + userIdStr + "-" + System.currentTimeMillis())
                            .build();
                    return user;
                })
                // 使用 redis 发送消息
                .mqType(LightCache.MQType.REDIS)
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

        // 另一个线程间隔15秒修改一次数据 发送消息
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(15000);

                    // 设置完成后，使用 rocketmq 发送消息
                    try {

                        DataChangeMsg dataChangeMsg = DataChangeMsg.builder()
                                .dataName(USER)
                                .dataId("1")
                                .type(DataChangeMsg.DataChangeType.UPDATE)
                                .build();

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
