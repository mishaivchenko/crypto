package com.crypto.funding.application.security;

import com.crypto.funding.config.CloudflareAccessProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class OperatorUsernameMigrationRunner implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger( OperatorUsernameMigrationRunner.class );

    private final CloudflareAccessProperties properties;
    private final OperatorAccountService operatorAccountService;

    public OperatorUsernameMigrationRunner(
        CloudflareAccessProperties properties,
        OperatorAccountService operatorAccountService
    )
    {
        this.properties = properties;
        this.operatorAccountService = operatorAccountService;
    }

    @Override
    public void run( ApplicationArguments args )
    {
        String migrations = properties.getUsernameMigrations();
        if( migrations == null || migrations.isBlank() )
        {
            return;
        }
        Arrays.stream( migrations.split( "," ) )
              .map( String::trim )
              .filter( entry -> !entry.isEmpty() )
              .forEach( this::applyMigration );
    }

    private void applyMigration( String entry )
    {
        String[] parts = entry.split( ":", 2 );
        if( parts.length != 2 || parts[0].isBlank() || parts[1].isBlank() )
        {
            log.warn( "Skipping invalid username migration entry (expected old:new): {}", entry );
            return;
        }
        String oldUsername = parts[0].trim();
        String newUsername = parts[1].trim();
        operatorAccountService.renameUsername( oldUsername, newUsername );
        log.info( "Operator username migration applied: {} → {}", oldUsername, newUsername );
    }
}
