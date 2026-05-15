package com.aipay.core.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.aipay.core.mapper")
public class CoreConfig {}
