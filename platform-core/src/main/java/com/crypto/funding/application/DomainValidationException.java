package com.crypto.funding.application;

public class DomainValidationException extends RuntimeException
{
    public DomainValidationException( String message )
    {
        super( message );
    }
}
