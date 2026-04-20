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
            SECURITY_OPERATOR_BOOTSTRAP_USERS=alice:base-token
            # comment
            """.stripIndent() );
        Files.writeString( tempDir.resolve( ".env.local" ), """
            SECURITY_OPERATOR_BOOTSTRAP_USERS=alice:local-token
            CREDENTIALS_STORAGE_ENABLED=true
            """.stripIndent() );

        Map<String, Object> properties = DotenvEnvironmentPostProcessor.loadDotenvProperties( tempDir );

        assertThat( properties )
            .containsEntry( "SECURITY_OPERATOR_BOOTSTRAP_USERS", "alice:local-token" )
            .containsEntry( "CREDENTIALS_STORAGE_ENABLED", "true" );
    }

    @Test
    void loadsDotenvFromParentDirectories()
        throws Exception
    {
        Path repoRoot = Files.createDirectories( tempDir.resolve( "repo" ) );
        Path moduleDir = Files.createDirectories( repoRoot.resolve( "monitor-app" ) );

        Files.writeString( repoRoot.resolve( ".env" ), """
            INTERNAL_ENGINE_TOKEN=repo-token
            """.stripIndent() );
        Files.writeString( moduleDir.resolve( ".env.local" ), """
            INTERNAL_ENGINE_TOKEN=module-token
            """.stripIndent() );

        Map<String, Object> properties = DotenvEnvironmentPostProcessor.loadDotenvProperties( moduleDir );

        assertThat( properties ).containsEntry( "INTERNAL_ENGINE_TOKEN", "module-token" );
    }

    @Test
    void loadsDotenvFromGitWorktreeRepositoryRoot()
        throws Exception
    {
        Path repoRoot = Files.createDirectories( tempDir.resolve( "main-repo" ) );
        Path gitCommonDir = Files.createDirectories( repoRoot.resolve( ".git/worktrees/funding-worktree" ) );
        Path worktreeRoot = Files.createDirectories( tempDir.resolve( "worktree" ) );

        Files.writeString( repoRoot.resolve( ".env" ), """
            BYBIT_TESTNET_API_KEY=repo-test-key
            """.stripIndent() );
        Files.writeString(
            worktreeRoot.resolve( ".git" ),
            "gitdir: " + gitCommonDir.toAbsolutePath().normalize()
        );

        Map<String, Object> properties = DotenvEnvironmentPostProcessor.loadDotenvProperties( worktreeRoot );

        assertThat( properties ).containsEntry( "BYBIT_TESTNET_API_KEY", "repo-test-key" );
    }
}
