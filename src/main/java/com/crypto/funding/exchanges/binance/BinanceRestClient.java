package com.crypto.funding.exchanges.binance;

import com.crypto.funding.exchanges.ExchangeRestClient;
import com.crypto.funding.trading.*;
import com.crypto.funding.utills.SymbolMapper;
import com.crypto.funding.watchlist.FundingInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.crypto.funding.utills.SymbolMapper.toExchange;

@Service
public class BinanceRestClient implements ExchangeRestClient, ExchangeTradingClient
{

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value( "${trading.binance.base-url}" )
    private String baseUrl;

    @Value( "${trading.binance.api-key:}" )
    private String apiKey;

    @Value( "${trading.binance.secret-key:}" )
    private String secretKey;

    @Value( "${trading.binance.recv-window:5000}" )
    private long recvWindow;

    @Override
    public String name()
    {
        return "binance";
    }

    @Override
    public TestOrderResult placeTestOrder( PlaceTestOrderCommand cmd ) throws Exception
    {
        ensureConfigured();

        // Предполагаю, что cmd.symbolUnified уже в формате BINANCE (BTCUSDT и т.п.).
        // Если нужен маппинг – сделай его ДО вызова Engine (через твой SymbolMapper).
        String symbol = cmd.symbolUnified();

        long timestamp = System.currentTimeMillis();

        Map<String, String> params = new LinkedHashMap<>();
        params.put( "symbol", toExchange(symbol) );
        params.put( "side", cmd.side().name() );            // BUY / SELL
        params.put( "type", cmd.type().name() );            // MARKET / LIMIT
        params.put( "quantity", cmd.quantity().toPlainString() );
        if( cmd.type() == OrderType.LIMIT && cmd.price() != null )
        {
            params.put( "price", cmd.price().toPlainString() );
            params.put( "timeInForce", "GTC" );
        }
        params.put( "timestamp", String.valueOf( timestamp ) );
        params.put( "recvWindow", String.valueOf( recvWindow ) );

        String queryString = params.entrySet().stream()
                                   .sorted( Map.Entry.comparingByKey() )
                                   .map( e -> e.getKey() + "=" + urlEncode( e.getValue() ) )
                                   .collect( Collectors.joining( "&" ) );

        String signature = HmacSigner.hmacSha256( secretKey, queryString );

        String url = baseUrl + "/fapi/v1/order" +
                     "?" + queryString +
                     "&signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( url ) )
                                         .timeout( Duration.ofSeconds( 5 ) )
                                         .header( "X-MBX-APIKEY", apiKey )
                                         .POST( HttpRequest.BodyPublishers.noBody() )
                                         .build();

        HttpResponse<String> response = http.send( request, HttpResponse.BodyHandlers.ofString() );

        if( response.statusCode() >= 300 )
        {
            throw new RuntimeException( "Binance testnet order failed: " + response.statusCode() +
                                        " body=" + response.body() );
        }

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
            symbol,
            cmd.side(),
            cmd.type(),
            quantity,
            price,
            status,
            System.currentTimeMillis()
        );
    }

    private void ensureConfigured()
    {
        if( apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank() )
        {
            throw new IllegalStateException( "Binance testnet API key/secret not configured" );
        }
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
            secondsToFunding
        );
    }

    @JsonIgnoreProperties( ignoreUnknown = true )
    static class PremiumIndex
    {
        public String symbol;
        public String lastFundingRate;
        public long nextFundingTime;
    }
}
