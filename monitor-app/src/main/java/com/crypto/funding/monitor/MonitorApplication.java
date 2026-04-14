package com.crypto.funding.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.crypto.funding")
public class MonitorApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run( MonitorApplication.class, args );
    }
}
