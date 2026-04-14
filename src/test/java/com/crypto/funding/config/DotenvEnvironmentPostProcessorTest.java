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

    @Test
    void loadsDotenvFromParentDirectories()
        throws Exception
    {
        Path repoRoot = Files.createDirectories( tempDir.resolve( "repo" ) );
        Path moduleDir = Files.createDirectories( repoRoot.resolve( "monitor-app" ) );

        Files.writeString( repoRoot.resolve( ".env" ), """
            BINANCE_PROD_API_KEY=repo-key
            """.stripIndent() );
        Files.writeString( moduleDir.resolve( ".env.local" ), """
            BINANCE_PROD_API_KEY=module-key
            """.stripIndent() );

        Map<String, Object> properties = DotenvEnvironmentPostProcessor.loadDotenvProperties( moduleDir );

        assertThat( properties ).containsEntry( "BINANCE_PROD_API_KEY", "module-key" );
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
