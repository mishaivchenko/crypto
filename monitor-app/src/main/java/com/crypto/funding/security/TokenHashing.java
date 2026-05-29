package com.crypto.funding.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class TokenHashing
{
    private static final String PREFIX = "sha256:";

    private TokenHashing()
    {
    }

    public static String normalizeHashOrHashRaw( String value )
    {
        if( value == null || value.isBlank() )
        {
            throw new IllegalArgumentException( "token must not be blank" );
        }
        String trimmed = value.trim();
        if( trimmed.startsWith( PREFIX ) )
        {
            return trimmed.substring( PREFIX.length() ).toLowerCase();
        }
        return sha256Hex( trimmed );
    }

    public static String sha256Hex( String token )
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance( "SHA-256" );
            byte[] hashed = digest.digest( token.getBytes( StandardCharsets.UTF_8 ) );
            StringBuilder builder = new StringBuilder( hashed.length * 2 );
            for( byte value : hashed )
            {
                builder.append( String.format( "%02x", value ) );
            }
            return builder.toString();
        }
        catch( NoSuchAlgorithmException ex )
        {
            throw new IllegalStateException( "SHA-256 is not available", ex );
        }
    }
}
