package com.crypto.funding.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import(EngineModuleConfiguration.class)
public class EngineApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run( EngineApplication.class, args );
    }
}
