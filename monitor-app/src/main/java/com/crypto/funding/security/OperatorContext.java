package com.crypto.funding.security;

import com.crypto.funding.application.DomainValidationException;

import java.util.Optional;

public final class OperatorContext
{
    private static final ThreadLocal<OperatorPrincipal> CURRENT = new ThreadLocal<>();

    private OperatorContext()
    {
    }

    public static void set( OperatorPrincipal principal )
    {
        CURRENT.set( principal );
    }

    public static OperatorPrincipal require()
    {
        OperatorPrincipal principal = CURRENT.get();
        if( principal == null )
        {
            throw new DomainValidationException( "Operator context is required." );
        }
        return principal;
    }

    public static Optional<OperatorPrincipal> current()
    {
        return Optional.ofNullable( CURRENT.get() );
    }

    public static void clear()
    {
        CURRENT.remove();
    }
}
