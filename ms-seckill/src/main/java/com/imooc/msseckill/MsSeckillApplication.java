package com.imooc.msseckill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.imooc.msseckill.mapper")
@SpringBootApplication
public class MsSeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsSeckillApplication.class, args);
    }

}
