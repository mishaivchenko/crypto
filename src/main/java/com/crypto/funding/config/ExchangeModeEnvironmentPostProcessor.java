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

        resolveExchange( environment, overrides, "bybit" );
        resolveExchange( environment, overrides, "gate" );
        resolveExchange( environment, overrides, "bitget" );
        resolveExchange( environment, overrides, "okx" );
        resolveExchange( environment, overrides, "kucoin" );

        if( !overrides.isEmpty() )
        {
            environment.getPropertySources().addFirst( new MapPropertySource( "exchangeModeOverrides", overrides ) );
        }
    }

    private static void resolveExchange( ConfigurableEnvironment environment, Map<String, Object> overrides, String exchange )
    {
        String globalMode = environment.getProperty(
            "trading.venue-access.mode",
            environment.getProperty( "trading." + exchange + ".mode", "production" )
        );
        boolean production = !supportsTestnet( exchange ) || "prod".equalsIgnoreCase( globalMode ) || "production".equalsIgnoreCase( globalMode );
        String prefix = "trading." + exchange + ".";
        String envPrefix = prefix + ( production ? "production." : "testnet." );

        setIfMissing( environment, overrides, prefix + "base-url", envPrefix + "base-url" );
        setIfMissing( environment, overrides, prefix + "api-key", envPrefix + "api-key" );
        setIfMissing( environment, overrides, prefix + "secret-key", envPrefix + "secret-key" );
        setIfMissing( environment, overrides, prefix + "passphrase", envPrefix + "passphrase" );

        String upper = exchange.toUpperCase();
        setIfMissingFromList(
            environment,
            overrides,
            prefix + "api-key",
            upper + "_API_KEY"
        );
        setIfMissingFromList(
            environment,
            overrides,
            prefix + "secret-key",
            upper + "_SECRET_KEY"
        );
        setIfMissingFromList(
            environment,
            overrides,
            prefix + "passphrase",
            upper + "_PASSPHRASE",
            upper + "_TESTNET_PASSPHRASE",
            upper + "_PROD_PASSPHRASE"
        );
    }

    private static boolean supportsTestnet( String exchange )
    {
        return "bybit".equalsIgnoreCase( exchange ) || "gate".equalsIgnoreCase( exchange );
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

    private static void setIfMissingFromList(
        ConfigurableEnvironment environment,
        Map<String, Object> overrides,
        String targetKey,
        String... sourceKeys
    )
    {
        String current = environment.getProperty( targetKey );
        if( current != null && !current.isBlank() )
        {
            return;
        }
        for( String sourceKey : sourceKeys )
        {
            String source = environment.getProperty( sourceKey );
            if( source != null && !source.isBlank() )
            {
                overrides.put( targetKey, source );
                return;
            }
        }
    }

    @Override
    public int getOrder()
    {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
