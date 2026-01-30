package com.crypto.funding.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ExchangeConfigDiagnostics implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger( ExchangeConfigDiagnostics.class );

    private final Environment environment;

    public ExchangeConfigDiagnostics( Environment environment )
    {
        this.environment = environment;
    }

    @Override
    public void run( ApplicationArguments args )
    {
        logExchange( "binance", "BINANCE" );
        logExchange( "bybit", "BYBIT" );
        logExchange( "gate", "GATE" );
    }

    private void logExchange( String exchange, String envPrefix )
    {
        String mode = envOrProp( "trading." + exchange + ".mode", envPrefix + "_MODE" );
        String baseUrl = envOrProp( "trading." + exchange + ".base-url", envPrefix + "_BASE_URL" );

        boolean apiKeyPresent = isPresent(
            "trading." + exchange + ".api-key",
            envPrefix + "_API_KEY",
            envPrefix + "_TESTNET_API_KEY",
            envPrefix + "_PROD_API_KEY"
        );
        boolean secretKeyPresent = isPresent(
            "trading." + exchange + ".secret-key",
            envPrefix + "_SECRET_KEY",
            envPrefix + "_TESTNET_SECRET_KEY",
            envPrefix + "_PROD_SECRET_KEY"
        );

        log.info(
            "[config] {} mode={} baseUrl={} apiKeyPresent={} secretKeyPresent={}",
            exchange,
            mode == null || mode.isBlank() ? "n/a" : mode,
            baseUrl == null || baseUrl.isBlank() ? "n/a" : baseUrl,
            apiKeyPresent,
            secretKeyPresent
        );
    }

    private boolean isPresent( String... keys )
    {
        for( String key : keys )
        {
            String value = environment.getProperty( key );
            if( value != null && !value.isBlank() )
            {
                return true;
            }
        }
        return false;
    }

    private String envOrProp( String propKey, String envKey )
    {
        String propValue = environment.getProperty( propKey );
        if( propValue != null && !propValue.isBlank() )
        {
            return propValue;
        }
        return environment.getProperty( envKey );
    }
}
