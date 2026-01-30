package com.crypto.funding.exchanges.binance;

import com.crypto.funding.exchanges.AbstractRestClient;
import com.crypto.funding.exchanges.Exchange;
import com.crypto.funding.exchanges.ExchangeRestClient;
import com.crypto.funding.trading.*;
import com.crypto.funding.watchlist.FundingInfo;
import com.crypto.funding.watchlist.SymbolRules;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.crypto.funding.utills.SymbolMapper.toExchange;

@Service
public class BinanceRestClient extends AbstractRestClient implements ExchangeRestClient, ExchangeTradingClient
{

    private final ObjectMapper mapper = new ObjectMapper();

    public BinanceRestClient(
        @Value( "${trading.binance.base-url:https://testnet.binancefuture.com}" ) String baseUrl,
        @Value( "${trading.binance.api-key:${BINANCE_API_KEY:${BINANCE_TESTNET_API_KEY:${BINANCE_PROD_API_KEY:}}}}" ) String apiKey,
        @Value( "${trading.binance.secret-key:${BINANCE_SECRET_KEY:${BINANCE_TESTNET_SECRET_KEY:${BINANCE_PROD_SECRET_KEY:}}}}" ) String secretKey,
        @Value( "${trading.binance.recv-window:5000}" ) long recvWindow
    )
    {
        super( baseUrl, apiKey, secretKey, recvWindow );
    }

    @Override
    public String exchangeName()
    {
        return Exchange.BINANCE.id();
    }

    @Override
    public String name()
    {
        return "binance";
    }

    @Override
    public @NonNull TestOrderResult createOrderResult( PlaceTestOrderCommand cmd, HttpResponse<String> response ) throws JsonProcessingException
    {
        JsonNode root = mapper.readTree( response.body() );

        String orderId = root.path( "orderId" ).asText( null );
        if( orderId == null || orderId.isBlank() )
        {
            // На всякий случай — если структура поменяется
            orderId = root.path( "clientOrderId" ).asText( "UNKNOWN" );
        }

        String status = root.path( "status" ).asText( "NEW" );
        BigDecimal quantity = cmd.quantity();
        BigDecimal price = cmd.price() != null
                           ? cmd.price()
                           : new BigDecimal( root.path( "avgPrice" ).asText(
            root.path( "price" ).asText( "0" )
        ) );

        return new TestOrderResult(
            name(),
            orderId,
            cmd.symbolUnified(),
            cmd.side(),
            cmd.type(),
            quantity,
            price,
            status,
            System.currentTimeMillis()
        );
    }

    @Override
    public HttpRequest createHttpRequest( PlaceTestOrderCommand cmd )
    {
        Map<String, String> params = new LinkedHashMap<>();
        params.put( "symbol", toExchange( cmd.symbolUnified() ) );
        params.put( "side", cmd.side().name() );            // BUY / SELL
        params.put( "type", cmd.type().name() );            // MARKET / LIMIT
        params.put( "quantity", cmd.quantity().toPlainString() );
        if( cmd.type() == OrderType.LIMIT && cmd.price() != null )
        {
            params.put( "price", cmd.price().toPlainString() );
            params.put( "timeInForce", "GTC" );
        }
        long timestamp = System.currentTimeMillis();
        params.put( "timestamp", String.valueOf( timestamp ) );
        params.put( "recvWindow", String.valueOf( getRecvWindow() ) );

        String queryString = params.entrySet().stream()
                                   .sorted( Map.Entry.comparingByKey() )
                                   .map( e -> e.getKey() + "=" + urlEncode( e.getValue() ) )
                                   .collect( Collectors.joining( "&" ) );

        String signature = HmacSigner.hmacSha256( getSecretKey(), queryString );

        String url = getBaseUrl() + "/fapi/v1/order" +
                     "?" + queryString +
                     "&signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( url ) )
                                         .timeout( Duration.ofSeconds( 5 ) )
                                         .header( "X-MBX-APIKEY", getApiKey() )
                                         .POST( HttpRequest.BodyPublishers.noBody() )
                                         .build();
        return request;
    }

    private static String urlEncode( String value )
    {
        return URLEncoder.encode( value, StandardCharsets.UTF_8 );
    }

    @Override
    public FundingInfo fetchFunding( String unifiedSymbol ) throws Exception
    {
        // Пример unifiedSymbol = "BTC/USDT"
        String binanceSymbol = toExchange( unifiedSymbol ); // "BTCUSDT"
        String url = "https://fapi.binance.com/fapi/v1/premiumIndex?symbol=" + binanceSymbol;

        HttpRequest req = HttpRequest.newBuilder()
                                     .uri( URI.create( url ) )
                                     .timeout( Duration.ofSeconds( 5 ) )
                                     .GET()
                                     .build();

        HttpResponse<String> resp = http.send( req, HttpResponse.BodyHandlers.ofString() );

        PremiumIndex dto = mapper.readValue( resp.body(), PremiumIndex.class );

        double fundingRatePct = Double.parseDouble( dto.lastFundingRate ) * 100.0;
        Instant nextFundingAt = Instant.ofEpochMilli( dto.nextFundingTime );
        long secondsToFunding = Duration.between( Instant.now(), nextFundingAt ).getSeconds();

        return new FundingInfo(
            name(),
            unifiedSymbol,
            fundingRatePct,
            nextFundingAt,
            secondsToFunding,
            BigDecimal.ZERO
        );
    }

    @Override
    public SymbolRules fetchRules( String unifiedSymbol )
    {
        return null;
    }

    @JsonIgnoreProperties( ignoreUnknown = true )
    static class PremiumIndex
    {
        public String symbol;
        public String lastFundingRate;
        public long nextFundingTime;
    }
}
