package com.oneinstep.light.cache.core.util;

import java.util.Collections;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * spring bean 工具类
 */
@Slf4j
@Component
public class SpringBeanUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    /**
     * 根据类型获取spring 容器里面的bean
     *
     * @param clazz 类型class
     * @param <T>   类型
     * @return bean
     */
    public static <T> T getBean(Class<T> clazz) {
        if (applicationContext == null) {
            log.warn("ApplicationContext is not initialized yet");
            return null;
        }
        try {
            return applicationContext.getBean(clazz);
        } catch (Exception e) {
            log.error("Failed to get bean of type {}", clazz.getName(), e);
            return null;
        }
    }

    public static <T> T getBean(String beanName, Class<T> clazz) {
        if (applicationContext == null) {
            log.warn("ApplicationContext is not initialized yet");
            return null;
        }
        try {
            return applicationContext.getBean(beanName, clazz);
        } catch (Exception e) {
            log.error("Failed to get bean", e);
            return null;
        }
    }

    /**
     * 获取某个类的所有实例
     *
     * @param clazz 类型class
     * @param <T>   类型
     * @return bean
     */
    public static <T> Map<String, T> getBeanOfType(Class<T> clazz) {
        if (applicationContext == null) {
            log.warn("ApplicationContext is not initialized yet");
            return Collections.emptyMap();
        }
        return applicationContext.getBeansOfType(clazz);
    }

}