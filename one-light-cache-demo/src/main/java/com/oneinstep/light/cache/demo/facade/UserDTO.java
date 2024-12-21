package com.oneinstep.light.cache.demo.facade;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UserDTO {

    private String userId;

    private String userName;

    private LocalDateTime createTime;
}
