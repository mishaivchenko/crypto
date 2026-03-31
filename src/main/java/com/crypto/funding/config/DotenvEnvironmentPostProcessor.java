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
import java.util.List;
import java.util.Map;

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
        mergeFile( properties, directory.resolve( ".env" ) );
        mergeFile( properties, directory.resolve( ".env.local" ) );
        return properties;
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
