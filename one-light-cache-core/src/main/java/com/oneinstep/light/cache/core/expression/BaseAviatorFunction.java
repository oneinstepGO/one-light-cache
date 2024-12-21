package com.oneinstep.light.cache.core.expression;

import org.apache.commons.lang3.StringUtils;

import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;
import com.oneinstep.light.cache.core.annotation.AviatorFunction;

import lombok.extern.slf4j.Slf4j;

/**
 * 基础 Aviator 函数
 */
@Slf4j
public abstract class BaseAviatorFunction extends AbstractFunction {

    @Override
    public String getName() {
        // 首先从注解中获取名称
        AviatorFunction annotation = getClass().getAnnotation(AviatorFunction.class);
        if (annotation != null && StringUtils.isNotBlank(annotation.name())) {
            return annotation.name();
        }
        // 如果注解中没有名称，则使用类名(首字母小写)作为函数名
        // GetUserByIdFunction -> getUserByIdFunction
        String className = getClass().getSimpleName();
        return StringUtils.uncapitalize(className);

    }

    /**
     * 包装返回值
     *
     * @param obj 返回值
     * @return 包装后的返回值
     */
    protected AviatorObject wrapReturn(Object obj) {
        return AviatorRuntimeJavaType.valueOf(obj);
    }

}