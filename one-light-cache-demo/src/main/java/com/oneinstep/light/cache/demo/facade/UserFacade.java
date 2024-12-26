package com.oneinstep.light.cache.demo.facade;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component("userFacade")
public class UserFacade {
    
    public UserDTO getUser(String userId) {
        return UserDTO.builder()
                .userId(userId)
                .userName("test-" + userId)
                .createTime(LocalDateTime.now())
                .build();
    }
    
}
