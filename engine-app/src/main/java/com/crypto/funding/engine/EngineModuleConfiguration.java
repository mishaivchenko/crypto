package com.crypto.funding.engine;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConfigurationPropertiesScan(basePackages = "com.crypto.funding.engine")
@EnableConfigurationProperties(EngineProperties.class)
@Import({
    EnginePlanClient.class,
    EnginePlanService.class,
    EngineController.class
})
public class EngineModuleConfiguration
{
}
