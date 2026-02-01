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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.crypto.funding.utills.SymbolMapper.toExchange;
import static com.crypto.funding.utills.SymbolMapper.toUnified;

@Service
public class BinanceRestClient extends AbstractRestClient implements ExchangeRestClient, ExchangeTradingClient
{
    private static final Logger log = LoggerFactory.getLogger( BinanceRestClient.class );

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
        String body = response.body();
        JsonNode root = (body == null || body.isBlank())
                        ? mapper.createObjectNode()
                        : mapper.readTree( body );

        String orderId = root.path( "orderId" ).asText( "" );
        if( orderId == null || orderId.isBlank() )
        {
            // /order/test on Binance returns {}. Fall back to a synthetic id.
            orderId = root.path( "clientOrderId" ).asText( "TEST_ORDER" );
        }

        String status = root.path( "status" ).asText( "NEW" );
        BigDecimal quantity = cmd.quantity();
        BigDecimal price = cmd.price() != null
                           ? cmd.price()
                           : parsePriceSafe( root );

        Long exchangeTs = root.hasNonNull("updateTime") ? root.get("updateTime").asLong() : null;

        return new TestOrderResult(
            name(),
            orderId,
            cmd.symbolUnified(),
            cmd.side(),
            cmd.type(),
            quantity,
            price,
            status,
            System.currentTimeMillis(),
            exchangeTs,
            exchangeTs == null ? OrderTimestampSource.UNKNOWN : OrderTimestampSource.RESPONSE_BODY
        );
    }

    private BigDecimal parsePriceSafe( JsonNode root )
    {
        String s = root.path( "avgPrice" ).asText( (String) null );
        if( s == null || s.isBlank() || "null".equalsIgnoreCase( s ) )
        {
            s = root.path( "price" ).asText( (String) null );
        }
        if( s == null || s.isBlank() || "null".equalsIgnoreCase( s ) )
        {
            return BigDecimal.ZERO;
        }
        try
        {
            return new BigDecimal( s );
        }
        catch( Exception ex )
        {
            log.warn( "[binance] price parse failed from response; defaulting to 0. raw={}", s );
            return BigDecimal.ZERO;
        }
    }

    @Override
    public TestOrderResult placeTestOrder( PlaceTestOrderCommand cmd ) throws Exception
    {
        ensureConfigured();

        int attempts = 0;
        Integer forcedScale = null;   // used to progressively reduce precision on -1111 errors
        RuntimeException lastError = null;

        while( attempts < 3 )
        {
            attempts++;
            BigDecimal normalizedQty = normalizeQuantity( cmd.quantity(), cmd.symbolUnified(), forcedScale );
            PlaceTestOrderCommand adjustedCmd = new PlaceTestOrderCommand(
                cmd.exchange(),
                cmd.symbolUnified(),
                cmd.side(),
                cmd.type(),
                normalizedQty,
                cmd.price()
            );

            HttpRequest request = createHttpRequest( adjustedCmd, normalizedQty );

            log.debug("Placing test order on {}: {} {} qty={} (attempt {} scaleOverride={})",
                cmd.exchange(), cmd.side(), cmd.symbolUnified(), normalizedQty, attempts, forcedScale);

            HttpResponse<String> response = http.send( request, HttpResponse.BodyHandlers.ofString() );
            long serverReceivedAt = System.currentTimeMillis();

            if( response.statusCode() < 300 )
            {
                TestOrderResult baseResult = createOrderResult( adjustedCmd, response );
                return baseResult.withTsMillis( serverReceivedAt );
            }

            String body = response.body();
            String errorMsg = "Order failed: " + response.statusCode() + " body=" + body;

            // Binance returns -1111 when decimals exceed allowed precision. Retry with coarser scale.
            if( body != null && body.contains( "\"code\":-1111" ) )
            {
                int nextScale = Math.max( normalizedQty.scale() - 1, 0 );
                // keep reducing until zero decimals; if already at zero — give up
                if( normalizedQty.scale() == 0 || (forcedScale != null && forcedScale == 0) )
                {
                    throw new RuntimeException( errorMsg );
                }

                forcedScale = nextScale;
                log.warn( "[binance] precision error (-1111) for {} qty={} (scale={}); retrying with scale {}", cmd.symbolUnified(), normalizedQty, normalizedQty.scale(), forcedScale );
                lastError = new RuntimeException( errorMsg );
                continue;
            }

            // any other error — bubble up
            validateResponse( response );
        }

        throw lastError != null ? lastError : new RuntimeException( "Failed to place test order on binance after retries" );
    }

    @Override
    public HttpRequest createHttpRequest( PlaceTestOrderCommand cmd )
    {
        BigDecimal normalizedQty = normalizeQuantity( cmd.quantity(), cmd.symbolUnified(), null );
        return createHttpRequest( cmd, normalizedQty );
    }

    private HttpRequest createHttpRequest( PlaceTestOrderCommand cmd, BigDecimal normalizedQty )
    {
        Map<String, String> params = new LinkedHashMap<>();
        params.put( "symbol", toExchange( cmd.symbolUnified() ) );
        params.put( "side", cmd.side().name() );            // BUY / SELL
        params.put( "type", cmd.type().name() );            // MARKET / LIMIT
        params.put( "quantity", normalizedQty.toPlainString() );
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

        String url = getBaseUrl() + "/fapi/v1/order/test" +
                     "?" + queryString +
                     "&signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( url ) )
                                         .timeout( Duration.ofSeconds( 5 ) )
                                         .header( "X-MBX-APIKEY", getApiKey() )
                                         .POST( HttpRequest.BodyPublishers.noBody() )
                                         .build();
        log.debug("[binance] normalizedQty={} for {}", normalizedQty, cmd.symbolUnified());
        return request;
    }

    private BigDecimal normalizeQuantity( BigDecimal qty, String unifiedSymbol, Integer maxScaleOverride )
    {
        try
        {
            BinanceSymbolMeta meta = fetchSymbolMeta( unifiedSymbol );
            BigDecimal step = meta.stepSize();

            String unified = toUnified( unifiedSymbol );
            String flat = unified.replace( "/", "" );
            // Temporary safety overrides for assets that reject 3dp quantities on testnet despite metadata.
            if( "SOLUSDT".equalsIgnoreCase( flat ) || "DOTUSDT".equalsIgnoreCase( flat ) )
            {
                step = new BigDecimal( "0.1" );
                meta = new BinanceSymbolMeta( step, step, meta.minNotional(), 1 );
                // also cap size to avoid margin errors on testnet
                BigDecimal cap = new BigDecimal( "5" );
                if( qty.compareTo( cap ) > 0 )
                {
                    qty = cap;
                }
            }

            BigDecimal steps = qty.divide( step, 0, RoundingMode.DOWN );
            BigDecimal floored = steps.multiply( step );

            int stepScale = Math.max( step.stripTrailingZeros().scale(), 0 );
            Integer qp = meta.quantityPrecision();

            int targetScale = ( qp != null ) ? qp : stepScale;
            if( maxScaleOverride != null )
            {
                targetScale = Math.min( targetScale, Math.max( maxScaleOverride, 0 ) );
            }
            // Never exceed the step's scale to avoid sending cosmetic trailing zeros that may still breach precision checks
            targetScale = Math.min( targetScale, stepScale );
            BigDecimal normalized = floored.setScale( targetScale, RoundingMode.DOWN );
            BigDecimal compact = normalized.stripTrailingZeros();
            if( compact.scale() < 0 ) compact = compact.setScale( 0 );

            log.debug("[binance] qty normalization: symbol={} orig={} floored={} step={} stepScale={} qp={} targetScale={} final={} compacted={}",
                unified, qty, floored, step, stepScale, qp, targetScale, normalized, compact);
            return compact;
        }
        catch( Exception e )
        {
            // Fallback: trim trailing zeros and cap precision to 8 decimal places, which covers Binance futures limits.
            BigDecimal stripped = qty.stripTrailingZeros();
            int scale = Math.min( Math.max( stripped.scale(), 0 ), maxScaleOverride != null ? maxScaleOverride : 8 );
            return stripped.setScale( scale, RoundingMode.DOWN );
        }
    }

    private BigDecimal normalizeQuantity( BigDecimal qty, String unifiedSymbol )
    {
        return normalizeQuantity( qty, unifiedSymbol, null );
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
        BigDecimal price = BigDecimal.valueOf( Double.parseDouble( dto.indexPrice  ));

        return new FundingInfo(
            name(),
            unifiedSymbol,
            fundingRatePct,
            nextFundingAt,
            secondsToFunding,
            price
        );
    }

    @Override
    public Long fetchOrderTimestamp(String unifiedSymbol, String exchangeOrderId) throws Exception
    {
        if (exchangeOrderId == null || exchangeOrderId.isBlank()) {
            return null;
        }

        String query = "symbol=" + toExchange(unifiedSymbol) +
                       "&orderId=" + urlEncode(exchangeOrderId) +
                       "&recvWindow=" + getRecvWindow() +
                       "&timestamp=" + System.currentTimeMillis();
        String signature = HmacSigner.hmacSha256(getSecretKey(), query);
        String url = getBaseUrl() + "/fapi/v1/order" + "?" + query + "&signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(5))
            .header("X-MBX-APIKEY", getApiKey())
            .GET()
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        validateResponse(response);

        JsonNode root = mapper.readTree(response.body());
        long updateTime = root.path("updateTime").asLong(0L);
        return updateTime == 0L ? null : updateTime;
    }

    @Override
    public SymbolRules fetchRules( String unifiedSymbol )
    {
        try
        {
            BinanceSymbolMeta meta = fetchSymbolMeta( unifiedSymbol );
            return new SymbolRules( meta.minQty(), meta.stepSize(), meta.minNotional() );
        }
        catch( Exception e )
        {
            throw new RuntimeException( "Failed to fetch symbol rules from Binance for " + unifiedSymbol, e );
        }
    }

    private BinanceSymbolMeta fetchSymbolMeta( String unifiedSymbol ) throws Exception
    {
        String binanceSymbol = toExchange( unifiedSymbol );
        String url = getBaseUrl() + "/fapi/v1/exchangeInfo?symbol=" + binanceSymbol;

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( url ) )
                                         .timeout( Duration.ofSeconds( 5 ) )
                                         .GET()
                                         .build();

        HttpResponse<String> response = http.send( request, HttpResponse.BodyHandlers.ofString() );
        validateResponse( response );

        JsonNode root = mapper.readTree( response.body() );
        JsonNode symbols = root.path( "symbols" );

        if( !symbols.isArray() || symbols.isEmpty() )
        {
            throw new IllegalStateException( "Empty exchangeInfo.symbols for " + unifiedSymbol );
        }

        JsonNode symbolNode = symbols.get( 0 );
        JsonNode filters = symbolNode.path( "filters" );

        if( !filters.isArray() || filters.isEmpty() )
        {
            throw new IllegalStateException( "No filters for " + unifiedSymbol + " in exchangeInfo" );
        }

        BigDecimal minQty = null;
        BigDecimal stepSize = null;
        BigDecimal minNotional = null;
        Integer quantityPrecision = symbolNode.hasNonNull( "quantityPrecision" ) ? symbolNode.get( "quantityPrecision" ).asInt() : null;

        for( JsonNode f : filters )
        {
            String type = f.path( "filterType" ).asText( "" );
            switch( type )
            {
                case "LOT_SIZE" -> {
                    if( f.hasNonNull( "minQty" ) )
                    {
                        minQty = new BigDecimal( f.get( "minQty" ).asText() );
                    }
                    if( f.hasNonNull( "stepSize" ) )
                    {
                        stepSize = new BigDecimal( f.get( "stepSize" ).asText() );
                    }
                }
                case "MARKET_LOT_SIZE" -> {
                    // fallback if LOT_SIZE is absent
                    if( minQty == null && f.hasNonNull( "minQty" ) )
                    {
                        minQty = new BigDecimal( f.get( "minQty" ).asText() );
                    }
                    if( stepSize == null && f.hasNonNull( "stepSize" ) )
                    {
                        stepSize = new BigDecimal( f.get( "stepSize" ).asText() );
                    }
                }
                case "MIN_NOTIONAL" -> {
                    String value = null;
                    if( f.hasNonNull( "notional" ) )
                    {
                        value = f.get( "notional" ).asText();
                    }
                    else if( f.hasNonNull( "minNotional" ) )
                    {
                        value = f.get( "minNotional" ).asText();
                    }

                    if( value != null && !value.isBlank() )
                    {
                        minNotional = new BigDecimal( value );
                    }
                }
            }
        }

        if( minQty == null || stepSize == null )
        {
            throw new IllegalStateException( "LOT_SIZE filter missing for " + unifiedSymbol );
        }

        return new BinanceSymbolMeta( minQty, stepSize, minNotional, quantityPrecision );
    }

    private record BinanceSymbolMeta(
        BigDecimal minQty,
        BigDecimal stepSize,
        BigDecimal minNotional,
        Integer quantityPrecision
    ) {}

    @JsonIgnoreProperties( ignoreUnknown = true )
    static class PremiumIndex
    {
        public String symbol;
        public String lastFundingRate;
        public long nextFundingTime;
        public String indexPrice;
    }
}
