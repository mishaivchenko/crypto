package com.crypto.funding.telegram;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.crypto.funding.telegram")
@ConfigurationPropertiesScan(basePackages = "com.crypto.funding.telegram.config")
@EnableFeignClients(basePackages = "com.crypto.funding.telegram.client")
@EnableScheduling
public class TelegramBotApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run( TelegramBotApplication.class, args );
    }
}
