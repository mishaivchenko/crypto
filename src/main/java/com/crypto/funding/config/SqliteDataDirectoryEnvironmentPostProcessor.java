package com.crypto.funding.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SqliteDataDirectoryEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered
{
    private static final Logger log = LoggerFactory.getLogger( SqliteDataDirectoryEnvironmentPostProcessor.class );
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";

    @Override
    public void postProcessEnvironment( ConfigurableEnvironment environment, SpringApplication application )
    {
        String datasourceUrl = environment.getProperty( "spring.datasource.url" );
        Path dbPath = extractSqliteDbPath( datasourceUrl );
        if( dbPath == null )
        {
            return;
        }

        Path parent = dbPath.getParent();
        if( parent == null )
        {
            return;
        }

        try
        {
            Files.createDirectories( parent );
        }
        catch( Exception ex )
        {
            throw new IllegalStateException( "Failed to create SQLite data directory: " + parent, ex );
        }

        log.debug( "[sqlite] ensured data directory exists: {}", parent.toAbsolutePath() );
    }

    @Override
    public int getOrder()
    {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    static Path extractSqliteDbPath( String datasourceUrl )
    {
        if( datasourceUrl == null || !datasourceUrl.startsWith( SQLITE_PREFIX ) )
        {
            return null;
        }

        String rawPath = datasourceUrl.substring( SQLITE_PREFIX.length() );
        int queryIndex = rawPath.indexOf( '?' );
        if( queryIndex >= 0 )
        {
            rawPath = rawPath.substring( 0, queryIndex );
        }

        if( rawPath.isBlank() || ":memory:".equalsIgnoreCase( rawPath ) )
        {
            return null;
        }

        try
        {
            if( rawPath.startsWith( "file:" ) )
            {
                if( rawPath.startsWith( "file:./" ) || rawPath.startsWith( "file:../" ) )
                {
                    return Paths.get( rawPath.substring( "file:".length() ) );
                }
                return Paths.get( URI.create( rawPath ) );
            }
            return Paths.get( rawPath );
        }
        catch( IllegalArgumentException ex )
        {
            throw new IllegalStateException( "Unsupported SQLite datasource path: " + datasourceUrl, ex );
        }
    }
}
