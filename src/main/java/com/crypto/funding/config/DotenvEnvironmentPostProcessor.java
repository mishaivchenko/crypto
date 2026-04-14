package com.crypto.funding.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered
{
    @Override
    public void postProcessEnvironment( ConfigurableEnvironment environment, SpringApplication application )
    {
        Map<String, Object> properties = loadDotenvProperties( Path.of( "." ).toAbsolutePath().normalize() );
        if( properties.isEmpty() )
        {
            return;
        }

        MapPropertySource propertySource = new MapPropertySource( "dotenvProperties", properties );
        MutablePropertySources propertySources = environment.getPropertySources();
        if( propertySources.contains( StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME ) )
        {
            propertySources.addAfter( StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource );
        }
        else
        {
            propertySources.addLast( propertySource );
        }
    }

    @Override
    public int getOrder()
    {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    static Map<String, Object> loadDotenvProperties( Path directory )
    {
        Map<String, Object> properties = new LinkedHashMap<>();
        for( Path candidateDirectory : directoriesToScan( directory ) )
        {
            mergeFile( properties, candidateDirectory.resolve( ".env" ) );
            mergeFile( properties, candidateDirectory.resolve( ".env.local" ) );
        }
        return properties;
    }

    private static List<Path> directoriesToScan( Path start )
    {
        Set<Path> ordered = new LinkedHashSet<>();
        Path current = start;
        while( current != null )
        {
            ordered.add( current );
            current = current.getParent();
        }
        detectGitRepositoryRoot( start ).ifPresent( repoRoot -> {
            Path repoCurrent = repoRoot;
            while( repoCurrent != null )
            {
                ordered.add( repoCurrent );
                repoCurrent = repoCurrent.getParent();
            }
        } );
        return ordered.stream().toList().reversed();
    }

    private static java.util.Optional<Path> detectGitRepositoryRoot( Path start )
    {
        Path current = start;
        while( current != null )
        {
            Path gitPath = current.resolve( ".git" );
            if( Files.isDirectory( gitPath ) )
            {
                return java.util.Optional.of( current );
            }
            if( Files.isRegularFile( gitPath ) )
            {
                try
                {
                    String raw = Files.readString( gitPath ).trim();
                    String prefix = "gitdir:";
                    if( raw.startsWith( prefix ) )
                    {
                        Path gitDir = Path.of( raw.substring( prefix.length() ).trim() );
                        String marker = gitDir.getFileSystem().getSeparator() + ".git" + gitDir.getFileSystem().getSeparator() + "worktrees" + gitDir.getFileSystem().getSeparator();
                        String gitDirText = gitDir.normalize().toString();
                        int markerIndex = gitDirText.indexOf( marker );
                        if( markerIndex > 0 )
                        {
                            return java.util.Optional.of( Path.of( gitDirText.substring( 0, markerIndex ) ) );
                        }
                    }
                }
                catch( IOException ex )
                {
                    throw new IllegalStateException( "Failed to inspect git metadata: " + gitPath, ex );
                }
            }
            current = current.getParent();
        }
        return java.util.Optional.empty();
    }

    private static void mergeFile( Map<String, Object> properties, Path file )
    {
        if( !Files.isRegularFile( file ) )
        {
            return;
        }

        try
        {
            List<String> lines = Files.readAllLines( file );
            for( String rawLine : lines )
            {
                String line = rawLine.trim();
                if( line.isEmpty() || line.startsWith( "#" ) )
                {
                    continue;
                }
                if( line.startsWith( "export " ) )
                {
                    line = line.substring( "export ".length() ).trim();
                }

                int separator = line.indexOf( '=' );
                if( separator <= 0 )
                {
                    continue;
                }

                String key = line.substring( 0, separator ).trim();
                String value = line.substring( separator + 1 ).trim();
                if( key.isEmpty() )
                {
                    continue;
                }

                properties.put( key, unquote( value ) );
            }
        }
        catch( IOException ex )
        {
            throw new IllegalStateException( "Failed to load dotenv file: " + file, ex );
        }
    }

    private static String unquote( String value )
    {
        if( value.length() >= 2 )
        {
            char first = value.charAt( 0 );
            char last = value.charAt( value.length() - 1 );
            if( ( first == '"' && last == '"' ) || ( first == '\'' && last == '\'' ) )
            {
                return value.substring( 1, value.length() - 1 );
            }
        }
        return value;
    }
}
