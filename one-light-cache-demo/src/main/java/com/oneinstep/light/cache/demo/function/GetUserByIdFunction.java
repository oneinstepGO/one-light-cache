package com.oneinstep.light.cache.demo.function;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import com.googlecode.aviator.runtime.function.FunctionUtils;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.oneinstep.light.cache.core.annotation.AviatorFunction;
import com.oneinstep.light.cache.core.expression.BaseAviatorFunction;
import com.oneinstep.light.cache.demo.facade.UserFacade;

/**
 * 获取用户信息函数
 */
@AviatorFunction(name = "getUserById")
public class GetUserByIdFunction extends BaseAviatorFunction {

    @Autowired
    private UserFacade userFacade;

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject arg) {
        String id = FunctionUtils.getStringValue(arg, env);
        return wrapReturn(userFacade.getUser(id));
    }

}