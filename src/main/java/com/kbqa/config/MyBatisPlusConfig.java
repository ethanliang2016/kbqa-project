package com.kbqa.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.kbqa.mapper")
public class MyBatisPlusConfig {
}
