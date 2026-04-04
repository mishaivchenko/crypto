package com.crypto.funding.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.crypto.funding")
@ConfigurationPropertiesScan(basePackages = "com.crypto.funding")
@EnableScheduling
@EnableFeignClients(basePackages = "com.crypto.funding")
public class MonitorApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run( MonitorApplication.class, args );
    }
}
