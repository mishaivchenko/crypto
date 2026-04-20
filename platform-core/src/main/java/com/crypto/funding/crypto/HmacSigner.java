package com.crypto.funding.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class HmacSigner
{
    private HmacSigner()
    {
    }

    public static String hmacSha256( String secret, String message )
    {
        return hmac( "HmacSHA256", secret, message );
    }

    public static String hmacSha512( String secret, String message )
    {
        return hmac( "HmacSHA512", secret, message );
    }

    public static String hmacSha256Base64( String secret, String message )
    {
        return hmacBase64( "HmacSHA256", secret, message );
    }

    public static String hmacSha512Base64( String secret, String message )
    {
        return hmacBase64( "HmacSHA512", secret, message );
    }

    private static String hmac( String algorithm, String secret, String message )
    {
        try
        {
            Mac mac = Mac.getInstance( algorithm );
            mac.init( new SecretKeySpec( secret.getBytes( StandardCharsets.UTF_8 ), algorithm ) );
            byte[] raw = mac.doFinal( message.getBytes( StandardCharsets.UTF_8 ) );
            StringBuilder sb = new StringBuilder( raw.length * 2 );
            for( byte b : raw )
            {
                sb.append( String.format( "%02x", b ) );
            }
            return sb.toString();
        }
        catch( Exception e )
        {
            throw new IllegalStateException( "Failed to calculate " + algorithm + " signature", e );
        }
    }

    private static String hmacBase64( String algorithm, String secret, String message )
    {
        try
        {
            Mac mac = Mac.getInstance( algorithm );
            mac.init( new SecretKeySpec( secret.getBytes( StandardCharsets.UTF_8 ), algorithm ) );
            byte[] raw = mac.doFinal( message.getBytes( StandardCharsets.UTF_8 ) );
            return Base64.getEncoder().encodeToString( raw );
        }
        catch( Exception e )
        {
            throw new IllegalStateException( "Failed to calculate " + algorithm + " base64 signature", e );
        }
    }
}
