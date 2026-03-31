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
        for( String rawVenue : metadataSyncProperties.getEnabledVenues() )
        {
            String venue = rawVenue.trim().toLowerCase( Locale.ROOT );
            if( venue.isBlank() )
            {
                continue;
            }
            String mode = environment.getProperty( "trading." + venue + ".mode", "production" ).trim().toLowerCase( Locale.ROOT );
            String apiKey = environment.getProperty( "trading." + venue + "." + mode + ".api-key" );
            String secretKey = environment.getProperty( "trading." + venue + "." + mode + ".secret-key" );
            if( apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank() )
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
}
