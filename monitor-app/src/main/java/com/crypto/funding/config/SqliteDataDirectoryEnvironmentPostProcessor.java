package com.crypto.funding.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class SqliteDataDirectoryEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered
{
    private static final Logger log = LoggerFactory.getLogger( SqliteDataDirectoryEnvironmentPostProcessor.class );
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";

    @Override
    public void postProcessEnvironment( ConfigurableEnvironment environment, SpringApplication application )
    {
        String datasourceUrl = environment.getProperty( "spring.datasource.url" );
        if( datasourceUrl == null || !datasourceUrl.startsWith( SQLITE_PREFIX ) )
        {
            return;
        }

        Path rootDirectory = locateProjectRoot( Path.of( "." ).toAbsolutePath().normalize() );
        String rewrittenUrl = rewriteRelativeSqliteUrl( datasourceUrl, rootDirectory );
        if( rewrittenUrl != null && !rewrittenUrl.equals( datasourceUrl ) )
        {
            environment.getPropertySources().addFirst( new MapPropertySource(
                "sqlitePathOverrides",
                Map.of( "spring.datasource.url", rewrittenUrl )
            ) );
            datasourceUrl = rewrittenUrl;
        }

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

    static String rewriteRelativeSqliteUrl( String datasourceUrl, Path rootDirectory )
    {
        String rawPath = datasourceUrl.substring( SQLITE_PREFIX.length() );
        int queryIndex = rawPath.indexOf( '?' );
        String query = queryIndex >= 0 ? rawPath.substring( queryIndex ) : "";
        String pathPart = queryIndex >= 0 ? rawPath.substring( 0, queryIndex ) : rawPath;

        if( pathPart.isBlank() || ":memory:".equalsIgnoreCase( pathPart ) )
        {
            return datasourceUrl;
        }

        if( pathPart.startsWith( "file:" ) )
        {
            String innerPath = pathPart.substring( "file:".length() );
            Path candidate = Paths.get( innerPath );
            if( candidate.isAbsolute() )
            {
                return datasourceUrl;
            }
            return SQLITE_PREFIX + "file:" + rootDirectory.resolve( candidate ).normalize() + query;
        }

        Path candidate = Paths.get( pathPart );
        if( candidate.isAbsolute() )
        {
            return datasourceUrl;
        }
        return SQLITE_PREFIX + rootDirectory.resolve( candidate ).normalize() + query;
    }

    private static Path locateProjectRoot( Path start )
    {
        Path current = start;
        while( current != null )
        {
            if( Files.exists( current.resolve( "settings.gradle" ) ) || Files.isDirectory( current.resolve( ".git" ) ) )
            {
                return current;
            }
            current = current.getParent();
        }
        return start;
    }
}
