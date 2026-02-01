package com.crypto.funding.exchanges;

import com.crypto.funding.trading.PlaceTestOrderCommand;
import com.crypto.funding.trading.TestOrderResult;
import com.crypto.funding.watchlist.FundingInfo;
import com.crypto.funding.watchlist.SymbolRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public abstract class AbstractRestClient
{
    private static final Logger log = LoggerFactory.getLogger( AbstractRestClient.class );
    private final String baseUrl;

    private final String apiKey;

    private final String secretKey;

    private final long recvWindow;

    public final HttpClient http;

    protected AbstractRestClient( String baseUrl, String apiKey, String secretKey, long recvWindow )
    {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.recvWindow = recvWindow;
        http = HttpClient.newHttpClient();
    }

    public TestOrderResult placeTestOrder( PlaceTestOrderCommand cmd ) throws Exception
    {
        ensureConfigured();

        HttpRequest request = createHttpRequest( cmd );

        log.debug("Placing test order on {}: {} {}", cmd.exchange(), cmd.side(), cmd.symbolUnified());
        HttpResponse<String> response = http.send( request, HttpResponse.BodyHandlers.ofString() );
        long serverReceivedAt = System.currentTimeMillis();
        log.debug("{} order placed {}: status {}", cmd.side(), cmd.symbolUnified(), response.statusCode());

        validateResponse( response );

        TestOrderResult baseResult = createOrderResult( cmd, response );
        return baseResult.withTsMillis( serverReceivedAt );
    }

    protected abstract TestOrderResult createOrderResult( PlaceTestOrderCommand cmd, HttpResponse<String> response ) throws JsonProcessingException;

    protected abstract HttpRequest createHttpRequest( PlaceTestOrderCommand cmd ) throws Exception;

    /**
     * Optionally fetch the exchange-side execution/open time via a GET request if it was not present in the initial response.
     * Default implementation returns null to indicate no data.
     */
    public Long fetchOrderTimestamp(String unifiedSymbol, String exchangeOrderId) throws Exception {
        return null;
    }

    public abstract String exchangeName();
    //
    protected void ensureConfigured()
    {
        if (baseUrl == null || baseUrl.isEmpty() )
        {
            throw new IllegalStateException( "URL should not be empty for: " + exchangeName() );
        }
        if( apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank() )
        {
            throw new IllegalStateException( "API key/secret not configured for: " + exchangeName() );
        }
    }

    protected static void validateResponse( HttpResponse<String> response )
    {
        if( response.statusCode() >= 300 )
        {
            throw new RuntimeException( "Order failed: " + response.statusCode() +
                                        " body=" + response.body() );
        }
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public String getSecretKey()
    {
        return secretKey;
    }

    public long getRecvWindow()
    {
        return recvWindow;
    }

    public abstract FundingInfo fetchFunding(String unifiedSymbol) throws Exception;
    public abstract SymbolRules fetchRules(String unifiedSymbol);

    /**
     * Optional: close/cancel previously placed test order (best-effort).
     * Default implementation is a no-op; clients may override.
     */
    public void cancelTestOrder(String unifiedSymbol, String exchangeOrderId) throws Exception {
        log.debug("cancelTestOrder not implemented for {} (orderId={})", exchangeName(), exchangeOrderId);
    }
}
