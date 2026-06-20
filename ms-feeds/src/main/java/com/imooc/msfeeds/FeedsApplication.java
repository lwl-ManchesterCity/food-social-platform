package com.imooc.msfeeds;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.imooc.msfeeds.mapper")
@SpringBootApplication
public class FeedsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeedsApplication.class, args);
    }

}
