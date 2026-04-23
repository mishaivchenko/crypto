package com.crypto.funding.engine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class EngineRuntimeProfilesIntegrationTest
{
    @Test
    void localSafeProfileKeepsLoopAndMetricsDisabled()
    {
        try( ConfigurableApplicationContext context = runWithProfile( "local-safe" ) )
        {
            assertThat( context.getBean( EngineProperties.class ).isExecutionLoopEnabled() ).isFalse();
            assertThat( context.getBean( EngineMetricsPublishProperties.class ).isEnabled() ).isFalse();
        }
    }

    @Test
    void stagingProfileKeepsLoopDisabledButEnablesMetricsPublishing()
    {
        try( ConfigurableApplicationContext context = runWithProfile( "staging" ) )
        {
            assertThat( context.getBean( EngineProperties.class ).isExecutionLoopEnabled() ).isFalse();
            assertThat( context.getBean( EngineMetricsPublishProperties.class ).isEnabled() ).isTrue();
        }
    }

    @Test
    void prodLikeProfileKeepsLoopDisabledButEnablesMetricsPublishing()
    {
        try( ConfigurableApplicationContext context = runWithProfile( "prod-like" ) )
        {
            assertThat( context.getBean( EngineProperties.class ).isExecutionLoopEnabled() ).isFalse();
            assertThat( context.getBean( EngineMetricsPublishProperties.class ).isEnabled() ).isTrue();
        }
    }

    private ConfigurableApplicationContext runWithProfile( String profile )
    {
        return new SpringApplicationBuilder( EngineApplication.class )
            .profiles( profile )
            .properties(
                "spring.main.web-application-type=none",
                "spring.main.lazy-initialization=true",
                "engine.internal-token=test-internal-token",
                "engine.monitor-base-url=http://127.0.0.1:65535"
            )
            .run();
    }
}
