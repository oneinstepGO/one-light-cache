package com.oneinstep.light.cache.starter.configuration;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.oneinstep.light.cache.core.annotation.AviatorFunction;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

/**
 * Aviator 函数注册器
 */
@Slf4j
@Configuration
public class AviatorFunctionRegistrar implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        // 获取所有带有 @AviatorFunction 注解的 bean
        applicationContext.getBeansWithAnnotation(AviatorFunction.class)
                .forEach((name, bean) -> {
                    if (bean instanceof AbstractFunction function) {
                        AviatorEvaluator.addFunction(function);
                        log.info("Registered Aviator function: {}, the bean name is {}", function.getName(), name);
                    }
                });
    }

}