package com.oneinstep.light.cache.core.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 自定义Aviator函数
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface AviatorFunction {

    /**
     * 函数名称
     *
     * @return 函数名称
     */
    String name() default "";
}