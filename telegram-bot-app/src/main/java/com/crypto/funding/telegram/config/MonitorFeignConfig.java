package com.crypto.funding.telegram.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MonitorFeignConfig
{
    @Bean
    public RequestInterceptor monitorOperatorTokenInterceptor(
        @Value( "${monitor.operator-token:}" ) String operatorToken
    )
    {
        return template -> {
            if( operatorToken != null && !operatorToken.isBlank() )
            {
                template.header( "X-Operator-Token", operatorToken );
            }
        };
    }
}
