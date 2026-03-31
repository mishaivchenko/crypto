package com.crypto.funding.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DotenvEnvironmentPostProcessorTest
{
    @TempDir
    Path tempDir;

    @Test
    void loadsDotenvAndLetsEnvLocalOverride()
        throws Exception
    {
        Files.writeString( tempDir.resolve( ".env" ), """
            TG_BOT_USERNAME=bot-user
            TG_BOT_TOKEN=base-token
            # comment
            """.stripIndent() );
        Files.writeString( tempDir.resolve( ".env.local" ), """
            TG_BOT_TOKEN=local-token
            TELEGRAM_BOT_ENABLED=true
            """.stripIndent() );

        Map<String, Object> properties = DotenvEnvironmentPostProcessor.loadDotenvProperties( tempDir );

        assertThat( properties )
            .containsEntry( "TG_BOT_USERNAME", "bot-user" )
            .containsEntry( "TG_BOT_TOKEN", "local-token" )
            .containsEntry( "TELEGRAM_BOT_ENABLED", "true" );
    }
}
