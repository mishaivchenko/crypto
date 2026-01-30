package com.crypto.funding.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class ExchangeModeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered
{

    @Override
    public void postProcessEnvironment( ConfigurableEnvironment environment, SpringApplication application )
    {
        Map<String, Object> overrides = new HashMap<>();

        resolveExchange( environment, overrides, "binance" );
        resolveExchange( environment, overrides, "bybit" );
        resolveExchange( environment, overrides, "gate" );

        if( !overrides.isEmpty() )
        {
            environment.getPropertySources().addFirst( new MapPropertySource( "exchangeModeOverrides", overrides ) );
        }
    }

    private static void resolveExchange( ConfigurableEnvironment environment, Map<String, Object> overrides, String exchange )
    {
        String mode = environment.getProperty( "trading." + exchange + ".mode", "testnet" );
        boolean production = "prod".equalsIgnoreCase( mode ) || "production".equalsIgnoreCase( mode );
        String prefix = "trading." + exchange + ".";
        String envPrefix = prefix + ( production ? "production." : "testnet." );

        setIfMissing( environment, overrides, prefix + "base-url", envPrefix + "base-url" );
        setIfMissing( environment, overrides, prefix + "api-key", envPrefix + "api-key" );
        setIfMissing( environment, overrides, prefix + "secret-key", envPrefix + "secret-key" );
    }

    private static void setIfMissing(
        ConfigurableEnvironment environment,
        Map<String, Object> overrides,
        String targetKey,
        String sourceKey
    )
    {
        String current = environment.getProperty( targetKey );
        if( current != null && !current.isBlank() )
        {
            return;
        }
        String source = environment.getProperty( sourceKey );
        if( source != null && !source.isBlank() )
        {
            overrides.put( targetKey, source );
        }
    }

    @Override
    public int getOrder()
    {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
