package com.crypto.funding.application.port;

import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.domain.venue.VenueConnectionStatus;

import java.io.IOException;
import java.util.List;

public interface VenueCredentialCheckPort
{
    record Credentials(
        String venue,
        VenueAccessMode mode,
        String baseUrl,
        String apiKey,
        String secretKey,
        String passphrase
    )
    {
    }

    record Result(
        VenueConnectionStatus status,
        String message,
        Integer httpStatus
    )
    {
    }

    String venue();

    List<VenueAccessMode> supportedModes();

    Result check( Credentials credentials ) throws IOException, InterruptedException;
}
