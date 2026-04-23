package com.crypto.funding.config;

import com.crypto.funding.MonitorApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorRuntimeProfilesIntegrationTest
{
    private static final String TEST_MASTER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @TempDir
    Path tempDir;

    @Test
    void localSafeProfileDisablesOperatorAuthAndCredentialStorage()
    {
        try( ConfigurableApplicationContext context = runWithProfile( "local-safe", tempDir.resolve( "local-safe.sqlite" ) ) )
        {
            assertThat( context.getBean( OperatorSecurityProperties.class ).isAuthEnabled() ).isFalse();
            assertThat( context.getBean( CredentialStorageProperties.class ).isEnabled() ).isFalse();
            assertThat( context.getBean( MetadataSyncProperties.class ).isRequireCredentialsOnStartup() ).isFalse();
            assertThat( context.getBean( MonitorEngineMetricsProperties.class ).isEnabled() ).isFalse();
        }
    }

    @Test
    void stagingProfileEnablesAuthAndCredentialStorageButKeepsMetadataCredentialsOptional()
    {
        try( ConfigurableApplicationContext context = runWithProfile( "staging", tempDir.resolve( "staging.sqlite" ) ) )
        {
            assertThat( context.getBean( OperatorSecurityProperties.class ).isAuthEnabled() ).isTrue();
            assertThat( context.getBean( CredentialStorageProperties.class ).isEnabled() ).isTrue();
            assertThat( context.getBean( MetadataSyncProperties.class ).isRequireCredentialsOnStartup() ).isFalse();
            assertThat( context.getBean( MonitorEngineMetricsProperties.class ).isEnabled() ).isTrue();
        }
    }

    @Test
    void prodLikeProfileEnablesAuthAndCredentialStorageAndRequiresMetadataCredentials()
    {
        try( ConfigurableApplicationContext context = runWithProfile( "prod-like", tempDir.resolve( "prod-like.sqlite" ) ) )
        {
            assertThat( context.getBean( OperatorSecurityProperties.class ).isAuthEnabled() ).isTrue();
            assertThat( context.getBean( CredentialStorageProperties.class ).isEnabled() ).isTrue();
            assertThat( context.getBean( MetadataSyncProperties.class ).isRequireCredentialsOnStartup() ).isTrue();
            assertThat( context.getBean( MonitorEngineMetricsProperties.class ).isEnabled() ).isTrue();
        }
    }

    private ConfigurableApplicationContext runWithProfile( String profile, Path dbPath )
    {
        return new SpringApplicationBuilder( MonitorApplication.class )
            .profiles( profile )
            .run(
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=jdbc:sqlite:" + dbPath.toAbsolutePath(),
                "--credentials.storage.master-key-base64=" + TEST_MASTER_KEY,
                "--security.operators.bootstrap-users=",
                "--trading.candidate-source.enabled=false",
                "--trading.metadata.sync-on-startup=false",
                "--trading.metadata.schedule-enabled=false"
            );
    }
}
