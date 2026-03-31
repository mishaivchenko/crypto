package com.crypto.funding.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SqliteDataDirectoryEnvironmentPostProcessorTest
{
    @TempDir
    Path tempDir;

    @Test
    void createsMissingParentDirectoryForSqliteDatasource()
    {
        Path dbPath = tempDir.resolve( "runtime/data/fundingarb.db" );
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "spring.datasource.url", "jdbc:sqlite:" + dbPath + "?busy_timeout=5000" );

        SqliteDataDirectoryEnvironmentPostProcessor processor = new SqliteDataDirectoryEnvironmentPostProcessor();

        assertThatCode( () -> processor.postProcessEnvironment( environment, new SpringApplication() ) ).doesNotThrowAnyException();
        assertThat( Files.isDirectory( dbPath.getParent() ) ).isTrue();
    }

    @Test
    void ignoresNonSqliteDatasource()
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "spring.datasource.url", "jdbc:h2:mem:testdb" );

        SqliteDataDirectoryEnvironmentPostProcessor processor = new SqliteDataDirectoryEnvironmentPostProcessor();

        assertThatCode( () -> processor.postProcessEnvironment( environment, new SpringApplication() ) ).doesNotThrowAnyException();
    }

    @Test
    void supportsRelativeFileStyleSqliteUrls()
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "spring.datasource.url", "jdbc:sqlite:file:./build/test-relative.db?busy_timeout=5000" );

        SqliteDataDirectoryEnvironmentPostProcessor processor = new SqliteDataDirectoryEnvironmentPostProcessor();

        assertThatCode( () -> processor.postProcessEnvironment( environment, new SpringApplication() ) ).doesNotThrowAnyException();
        assertThat( Files.isDirectory( Path.of( "./build" ) ) ).isTrue();
    }
}
