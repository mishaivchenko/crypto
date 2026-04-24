package com.crypto.funding.engine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class EngineModuleConfigurationTest
{
    // REQ: ENG-RTC-003
    @Test
    void declaresExpectedImportsAndPropertyScanning()
    {
        assertThat( EngineModuleConfiguration.class.isAnnotationPresent( Configuration.class ) ).isTrue();

        ConfigurationPropertiesScan scan = EngineModuleConfiguration.class.getAnnotation( ConfigurationPropertiesScan.class );
        assertThat( scan ).isNotNull();
        assertThat( scan.basePackages() ).contains( "com.crypto.funding.engine" );

        EnableConfigurationProperties properties = EngineModuleConfiguration.class.getAnnotation( EnableConfigurationProperties.class );
        assertThat( properties ).isNotNull();
        assertThat( properties.value() ).contains( EngineProperties.class );

        Import imported = EngineModuleConfiguration.class.getAnnotation( Import.class );
        assertThat( imported ).isNotNull();
        assertThat( imported.value() ).containsExactlyInAnyOrder(
            EnginePlanClient.class,
            EnginePlanService.class,
            EngineController.class
        );
    }
}
