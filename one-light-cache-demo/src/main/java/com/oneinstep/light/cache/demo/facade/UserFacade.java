package com.oneinstep.light.cache.demo.facade;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

@Component("userFacade")
public class UserFacade {
    
    public UserDTO getUser(String userId) {
        UserDTO userDTO = new UserDTO();
        userDTO.setUserId(userId);
        userDTO.setUserName("test-" + userId);
        userDTO.setCreateTime(LocalDateTime.now());
        return userDTO;
    }
    
}
