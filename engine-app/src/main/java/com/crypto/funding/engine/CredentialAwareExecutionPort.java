package com.crypto.funding.engine;

import com.crypto.funding.application.port.ExecutionPort;
import com.crypto.funding.domain.execution.OrderAttempt;
import com.crypto.funding.domain.execution.OrderAttemptStatus;
import com.crypto.funding.domain.execution.OrderIntent;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

@Component
public class CredentialAwareExecutionPort implements ExecutionPort
{
    private final Environment environment;

    public CredentialAwareExecutionPort( Environment environment )
    {
        this.environment = environment;
    }

    @Override
    public OrderAttempt submitOrder( Long armedTradeId, String venue, String symbol, OrderIntent intent )
    {
        Instant attemptedAt = Instant.now();
        String normalizedVenue = venue == null ? "" : venue.trim().toLowerCase( Locale.ROOT );
        String missingCredentials = missingCredentialsReason( normalizedVenue );
        if( missingCredentials != null )
        {
            return failed( armedTradeId, normalizedVenue, symbol, intent, attemptedAt, missingCredentials );
        }

        return failed(
            armedTradeId,
            normalizedVenue,
            symbol,
            intent,
            attemptedAt,
            "Engine order HTTP submission is not enabled in this phase. Credentials are present, but live order adapters are still guarded."
        );
    }

    private OrderAttempt failed(
        Long armedTradeId,
        String venue,
        String symbol,
        OrderIntent intent,
        Instant attemptedAt,
        String reason
    )
    {
        return new OrderAttempt(
            null,
            null,
            armedTradeId,
            null,
            venue,
            symbol,
            intent.side(),
            intent.executionType(),
            intent.quantity(),
            intent.limitPrice(),
            OrderAttemptStatus.FAILED,
            null,
            null,
            null,
            attemptedAt,
            null,
            reason,
            null,
            null
        );
    }

    private String missingCredentialsReason( String venue )
    {
        String apiKey = environment.getProperty( "engine.credentials." + venue + ".api-key" );
        String secretKey = environment.getProperty( "engine.credentials." + venue + ".secret-key" );
        if( apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank() )
        {
            return "Missing engine credentials for " + venue
                   + ". Configure engine.credentials." + venue + ".api-key and engine.credentials." + venue + ".secret-key.";
        }
        if( requiresPassphrase( venue ) )
        {
            String passphrase = environment.getProperty( "engine.credentials." + venue + ".passphrase" );
            if( passphrase == null || passphrase.isBlank() )
            {
                return "Missing engine passphrase for " + venue + ". Configure engine.credentials." + venue + ".passphrase.";
            }
        }
        return null;
    }

    private static boolean requiresPassphrase( String venue )
    {
        return "bitget".equals( venue ) || "okx".equals( venue ) || "kucoin".equals( venue );
    }
}
