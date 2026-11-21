package com.javaorm.sample;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商城管理后台样例应用启动类（MyBatis-Plus 兼容模式 + Vue 3）
 */
@SpringBootApplication
public class SampleMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleMallApplication.class, args);
    }
}
