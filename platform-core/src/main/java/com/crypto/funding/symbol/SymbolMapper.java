package com.crypto.funding.symbol;

public final class SymbolMapper
{
    private SymbolMapper()
    {
    }

    public static String toUnified( String raw )
    {
        if( raw == null )
        {
            return null;
        }
        String symbol = raw.trim().toUpperCase().replace( '-', '/' );
        if( symbol.endsWith( "PERP" ) && !symbol.contains( "/" ) )
        {
            symbol = symbol.substring( 0, symbol.length() - 4 );
        }
        if( !symbol.contains( "/" ) )
        {
            if( symbol.endsWith( "USDT" ) )
            {
                symbol = symbol.replace( "USDT", "/USDT" );
            }
            else if( symbol.endsWith( "USDC" ) )
            {
                symbol = symbol.replace( "USDC", "/USDC" );
            }
            else if( symbol.endsWith( "USDM" ) )
            {
                symbol = symbol.replace( "USDM", "/USDT" );
            }
            else
            {
                symbol = symbol + "/USDT";
            }
        }
        if( symbol.contains( "_" ) )
        {
            symbol = symbol.replace( "_", "" );
        }
        return symbol;
    }

    public static String toExchange( String unified )
    {
        return unified.replace( "/", "" );
    }

    public static String toBybit( String unified )
    {
        return unified.replace( "/", "" );
    }

    public static String toGate( String unified )
    {
        return unified.split( "/" )[0] + "_USDT";
    }
}
