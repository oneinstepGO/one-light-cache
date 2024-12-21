package com.oneinstep.light.cache.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.oneinstep.light.cache.starter.annotation.EnableLightCache;

@SpringBootApplication
@EnableLightCache
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}