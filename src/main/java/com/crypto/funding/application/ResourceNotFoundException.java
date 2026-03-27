package com.crypto.funding.application;

public class ResourceNotFoundException extends RuntimeException
{
    public ResourceNotFoundException( String message )
    {
        super( message );
    }
}
