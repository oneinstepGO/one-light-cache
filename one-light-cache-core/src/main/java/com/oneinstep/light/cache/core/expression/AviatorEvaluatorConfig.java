package com.oneinstep.light.cache.core.expression;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Feature;
import com.googlecode.aviator.Options;
import com.googlecode.aviator.spring.SpringContextFunctionLoader;

/**
 * Aviator 配置
 */
@Component
public class AviatorEvaluatorConfig implements ApplicationContextAware {

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        AviatorEvaluator.addFunctionLoader(new SpringContextFunctionLoader(applicationContext));
    }

    static {
        // 设置超时时间 15000ms
        AviatorEvaluator.setOption(Options.EVAL_TIMEOUT_MS, 15000);
        // 设置最大循环次数 1000
        AviatorEvaluator.setOption(Options.MAX_LOOP_COUNT, 1000);
        // 设置总是解析浮点数为小数 默认true
        AviatorEvaluator.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, true);
        // 设置总是解析整数为小数 默认true
        AviatorEvaluator.setOption(Options.ALWAYS_PARSE_INTEGRAL_NUMBER_INTO_DECIMAL, true);
        // 关闭模块系统 默认true
        AviatorEvaluator.getInstance().disableFeature(Feature.Module);
        // 设置函数缺失处理 默认null
        AviatorEvaluator.getInstance().setFunctionMissing(null);

        // 设置允许的类
        // final HashSet<Object> classes = new HashSet<>();
        // classes.add(String.class);
        // classes.add(Integer.class);
        // classes.add(Long.class);
        // classes.add(Double.class);
        // classes.add(Float.class);
        // classes.add(Boolean.class);
        // AviatorEvaluator.setOption(Options.ALLOWED_CLASS_SET, classes);

    }
}
