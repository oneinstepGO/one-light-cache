package com.oneinstep.light.cache.demo.result;

import lombok.Data;

@Data
public class Result<T> {

    private String code;

    private String message;

    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode("0000");
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(String code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
