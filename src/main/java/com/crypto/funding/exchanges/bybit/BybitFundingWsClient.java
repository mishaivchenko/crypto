package com.crypto.funding.exchanges.bybit;

import com.crypto.funding.exchanges.AbstractWsClient;
import com.crypto.funding.utills.SymbolMapper;
import com.crypto.funding.watchlist.FundingInfo;
import com.crypto.funding.watchlist.FundingWatchlistService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BybitFundingWsClient extends AbstractWsClient
{
    private final FundingWatchlistService fundingWatchlistService;

    public BybitFundingWsClient( FundingWatchlistService fundingWatchlistService )
    {
        this.fundingWatchlistService = fundingWatchlistService;
    }

    @Override
    public String name()
    {
        return "bybit";
    }

    @Override
    protected URI endpoint()
    {
        return URI.create( "wss://stream.bybit.com/v5/public/linear" );
    }

    @Override
    protected String buildSubscribeMessage( List<String> unifiedSymbols )
    {
        List<String> args = new ArrayList<>();
        for( String u : unifiedSymbols )
        {
            args.add( "tickers." + SymbolMapper.toBybit( u ) ); // "tickers.SDUSDT"
        }
        Map<String, Object> sub = Map.of(
            "op", "subscribe",
            "args", args
        );
        try
        {
            return mapper.writeValueAsString( sub );
        }
        catch( Exception e )
        {
            log.error( e.getMessage(), e );
            return null;
        }
    }

    @Override
    protected void handleTextMessage( String text ) throws Exception
    {
        JsonNode root = mapper.readTree( text );
        if( !root.has( "topic" ) || !root.path( "topic" ).asText().startsWith( "tickers" ) )
        {
            return;
        }
        JsonNode data = root.path( "data" );
        if( data.isArray() )
        {
            for( JsonNode n : data )
            {
                handleTicker( n );
            }
        }
        else if( data.isObject() )
        {
            handleTicker( data );
        }
    }

    private void handleTicker( JsonNode n )
    {
        String symbol = n.path( "symbol" ).asText( null );  // "BTCUSDT"
        if( symbol == null )
        {
            return;
        }
        String unified = SymbolMapper.toUnified( symbol );

        double ratePct = parseD( n.path( "fundingRate" ).asText( null ) ) * 100d;
        long nextMs = n.path( "nextFundingTime" ).asLong( 0 );
        if( ratePct == 0d )
        {
            return;
        }

        Instant next = null;
        long sec = 0;

        if (nextMs > 0 )
        {
            next = Instant.ofEpochMilli( nextMs );
            sec = Math.max( 0, Duration.between( Instant.now(), next ).toSeconds() );
        }

        fundingWatchlistService.updateFunding( new FundingInfo( name(), unified, ratePct, next, sec ) );
    }

    private static double parseD( String s )
    {
        if( s == null )
        {
            return 0d;
        }
        try
        {
            return Double.parseDouble( s );
        }
        catch( Exception e )
        {
            return 0d;
        }
    }
}
