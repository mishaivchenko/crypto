package com.crypto.funding.application.venue;

import com.crypto.funding.config.MetadataSyncProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class VenueCredentialStartupValidator implements ApplicationRunner
{
    private final MetadataSyncProperties metadataSyncProperties;
    private final Environment environment;

    public VenueCredentialStartupValidator(
        MetadataSyncProperties metadataSyncProperties,
        Environment environment
    )
    {
        this.metadataSyncProperties = metadataSyncProperties;
        this.environment = environment;
    }

    @Override
    public void run( ApplicationArguments args )
    {
        if( !metadataSyncProperties.isRequireCredentialsOnStartup() )
        {
            return;
        }

        List<String> missing = new ArrayList<>();
        String globalMode = environment.getProperty(
            "trading.venue-access.mode",
            environment.getProperty( "trading.bybit.mode", "testnet" )
        ).trim().toLowerCase( Locale.ROOT );
        for( String rawVenue : metadataSyncProperties.getEnabledVenues() )
        {
            String venue = rawVenue.trim().toLowerCase( Locale.ROOT );
            if( venue.isBlank() )
            {
                continue;
            }
            String mode = resolveModeForVenue( venue, globalMode );
            String apiKey = environment.getProperty( "trading." + venue + "." + mode + ".api-key" );
            String secretKey = environment.getProperty( "trading." + venue + "." + mode + ".secret-key" );
            String passphrase = environment.getProperty( "trading." + venue + "." + mode + ".passphrase" );
            boolean missingBasicKeys = apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank();
            boolean missingPassphrase = requiresPassphrase( venue ) && ( passphrase == null || passphrase.isBlank() );
            if( missingBasicKeys || missingPassphrase )
            {
                missing.add( venue );
            }
        }

        if( !missing.isEmpty() )
        {
            throw new IllegalStateException(
                "Missing exchange credentials for venues: " + String.join( ", ", missing ) +
                ". Provide keys or set trading.metadata.require-credentials-on-startup=false."
            );
        }
    }

    private boolean requiresPassphrase( String venue )
    {
        return "bitget".equalsIgnoreCase( venue )
               || "okx".equalsIgnoreCase( venue )
               || "kucoin".equalsIgnoreCase( venue );
    }

    private String resolveModeForVenue( String venue, String globalMode )
    {
        if( "production".equalsIgnoreCase( globalMode ) || "prod".equalsIgnoreCase( globalMode ) )
        {
            return "production";
        }
        if( "bybit".equalsIgnoreCase( venue ) || "gate".equalsIgnoreCase( venue ) )
        {
            return "testnet";
        }
        return "production";
    }
}
