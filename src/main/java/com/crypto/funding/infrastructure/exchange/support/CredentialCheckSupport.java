package com.crypto.funding.infrastructure.exchange.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class CredentialCheckSupport
{
    private CredentialCheckSupport()
    {
    }

    public static boolean looksLikeInvalidCredentials( String message )
    {
        if( message == null || message.isBlank() )
        {
            return false;
        }
        String normalized = message.toLowerCase( Locale.ROOT );
        return normalized.contains( "invalid" )
               || normalized.contains( "api key" )
               || normalized.contains( "apikey" )
               || normalized.contains( "api-key" )
               || normalized.contains( "signature" )
               || normalized.contains( "passphrase" )
               || normalized.contains( "permission" )
               || normalized.contains( "auth" )
               || normalized.contains( "credential" )
               || normalized.contains( "expired key" );
    }

    public static String sha512Hex( String value )
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance( "SHA-512" );
            byte[] hash = digest.digest( value.getBytes( StandardCharsets.UTF_8 ) );
            StringBuilder sb = new StringBuilder( hash.length * 2 );
            for( byte b : hash )
            {
                sb.append( String.format( "%02x", b ) );
            }
            return sb.toString();
        }
        catch( Exception ex )
        {
            throw new IllegalStateException( "Failed to calculate sha512 hash", ex );
        }
    }
}
