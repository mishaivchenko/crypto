package com.crypto.funding.engine.exchange;

import com.crypto.funding.application.port.ExecutionPort;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.crypto.HmacSigner;
import com.crypto.funding.domain.execution.ExecutionType;
import com.crypto.funding.domain.execution.OrderAttempt;
import com.crypto.funding.domain.execution.OrderAttemptStatus;
import com.crypto.funding.domain.execution.OrderIntent;
import com.crypto.funding.domain.trade.TradeSide;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class LiveExchangeExecutionPort implements ExecutionPort
{
    private static final long BYBIT_RECV_WINDOW_MS = 5000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds( 10 );

    private final Environment environment;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LiveExchangeExecutionPort( Environment environment )
    {
        this( environment, HttpClient.newHttpClient(), new ObjectMapper() );
    }

    protected LiveExchangeExecutionPort( Environment environment, HttpClient httpClient, ObjectMapper objectMapper )
    {
        this.environment = environment;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public OrderAttempt submitOrder( EngineExecutionPlan plan, OrderIntent intent, boolean reduceOnly )
    {
        Instant attemptedAt = Instant.now();
        String normalizedVenue = normalizeVenue( plan == null ? null : plan.venue() );
        String symbol = plan == null ? "" : plan.symbol();

        String missingCredentials = missingCredentialsReason( normalizedVenue );
        if( missingCredentials != null )
        {
            return failed( plan, normalizedVenue, symbol, intent, attemptedAt, missingCredentials );
        }

        String gateFailure = liveGateFailure( plan, normalizedVenue, intent, reduceOnly, attemptedAt );
        if( gateFailure != null )
        {
            return failed( plan, normalizedVenue, symbol, intent, attemptedAt, gateFailure );
        }

        try
        {
            if( "bybit".equals( normalizedVenue ) )
            {
                return submitBybit( plan, intent, reduceOnly, attemptedAt );
            }
            if( "gate".equals( normalizedVenue ) )
            {
                return submitGate( plan, intent, reduceOnly, attemptedAt );
            }
            return failed( plan, normalizedVenue, symbol, intent, attemptedAt, "Unsupported live order venue: " + normalizedVenue );
        }
        catch( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            return failed( plan, normalizedVenue, symbol, intent, attemptedAt, "Live order submission interrupted: " + e.getMessage() );
        }
        catch( Exception e )
        {
            return failed( plan, normalizedVenue, symbol, intent, attemptedAt, "Live order submission failed: " + e.getMessage() );
        }
    }

    private OrderAttempt submitBybit(
        EngineExecutionPlan plan,
        OrderIntent intent,
        boolean reduceOnly,
        Instant attemptedAt
    ) throws IOException, InterruptedException
    {
        String baseUrl = baseUrl( "bybit" );
        BigDecimal quantity = orderQuantity( plan, intent, reduceOnly );
        ObjectNode body = objectMapper.createObjectNode();
        body.put( "category", "linear" );
        body.put( "symbol", plan.venueSymbol() );
        body.put( "side", intent.side() == TradeSide.SHORT ? "Sell" : "Buy" );
        body.put( "orderType", "Market" );
        body.put( "qty", plain( quantity ) );
        body.put( "reduceOnly", reduceOnly );
        body.put( "positionIdx", 0 );
        body.put( "orderLinkId", orderLinkId( plan, reduceOnly ) );
        String bodyJson = objectMapper.writeValueAsString( body );

        long timestamp = System.currentTimeMillis();
        String apiKey = credential( "bybit", "api-key" );
        String secretKey = credential( "bybit", "secret-key" );
        String signPayload = timestamp + apiKey + BYBIT_RECV_WINDOW_MS + bodyJson;
        String sign = HmacSigner.hmacSha256( secretKey, signPayload );

        HttpRequest createRequest = HttpRequest.newBuilder()
                                               .uri( URI.create( baseUrl + "/v5/order/create" ) )
                                               .timeout( REQUEST_TIMEOUT )
                                               .header( "Content-Type", "application/json" )
                                               .header( "X-BAPI-API-KEY", apiKey )
                                               .header( "X-BAPI-TIMESTAMP", String.valueOf( timestamp ) )
                                               .header( "X-BAPI-RECV-WINDOW", String.valueOf( BYBIT_RECV_WINDOW_MS ) )
                                               .header( "X-BAPI-SIGN", sign )
                                               .POST( HttpRequest.BodyPublishers.ofString( bodyJson ) )
                                               .build();

        HttpResponse<String> createResponse = httpClient.send( createRequest, HttpResponse.BodyHandlers.ofString() );
        JsonNode createRoot = parseBody( createResponse.body() );
        if( createResponse.statusCode() >= 300 || createRoot.path( "retCode" ).asInt( -1 ) != 0 )
        {
            return rejected(
                plan,
                intent,
                quantity,
                attemptedAt,
                "Bybit order rejected: " + createRoot.path( "retMsg" ).asText( createResponse.body() )
            );
        }
        String orderId = createRoot.path( "result" ).path( "orderId" ).asText();
        return bybitStatus( plan, intent, quantity, orderId, attemptedAt );
    }

    private OrderAttempt bybitStatus(
        EngineExecutionPlan plan,
        OrderIntent intent,
        BigDecimal quantity,
        String orderId,
        Instant attemptedAt
    ) throws IOException, InterruptedException
    {
        String baseUrl = baseUrl( "bybit" );
        String query = "category=linear&symbol=" + encode( plan.venueSymbol() )
                       + "&orderId=" + encode( orderId )
                       + "&openOnly=1";
        long timestamp = System.currentTimeMillis();
        String apiKey = credential( "bybit", "api-key" );
        String secretKey = credential( "bybit", "secret-key" );
        String signPayload = timestamp + apiKey + BYBIT_RECV_WINDOW_MS + query;
        String sign = HmacSigner.hmacSha256( secretKey, signPayload );

        HttpRequest statusRequest = HttpRequest.newBuilder()
                                               .uri( URI.create( baseUrl + "/v5/order/realtime?" + query ) )
                                               .timeout( REQUEST_TIMEOUT )
                                               .header( "X-BAPI-API-KEY", apiKey )
                                               .header( "X-BAPI-TIMESTAMP", String.valueOf( timestamp ) )
                                               .header( "X-BAPI-RECV-WINDOW", String.valueOf( BYBIT_RECV_WINDOW_MS ) )
                                               .header( "X-BAPI-SIGN", sign )
                                               .GET()
                                               .build();
        HttpResponse<String> response = httpClient.send( statusRequest, HttpResponse.BodyHandlers.ofString() );
        JsonNode root = parseBody( response.body() );
        JsonNode order = root.path( "result" ).path( "list" ).isArray() && !root.path( "result" ).path( "list" ).isEmpty()
                         ? root.path( "result" ).path( "list" ).get( 0 )
                         : objectMapper.createObjectNode();
        String status = order.path( "orderStatus" ).asText();
        BigDecimal averagePrice = decimalOrNull( order.path( "avgPrice" ).asText( null ) );
        BigDecimal filledQuantity = decimalOrNull( order.path( "cumExecQty" ).asText( null ) );
        BigDecimal fee = decimalOrNull( order.path( "cumExecFee" ).asText( null ) );
        Instant exchangeTimestamp = millisInstant( order.path( "updatedTime" ).asText( null ), attemptedAt );
        return completed(
            plan,
            intent,
            quantity,
            "Filled".equalsIgnoreCase( status ) ? OrderAttemptStatus.FILLED : OrderAttemptStatus.ACKNOWLEDGED,
            orderId,
            attemptedAt,
            exchangeTimestamp,
            averagePrice,
            filledQuantity,
            fee
        );
    }

    private OrderAttempt submitGate(
        EngineExecutionPlan plan,
        OrderIntent intent,
        boolean reduceOnly,
        Instant attemptedAt
    ) throws IOException, InterruptedException
    {
        String baseUrl = baseUrl( "gate" );
        BigDecimal quantity = orderQuantity( plan, intent, reduceOnly );
        BigDecimal size = reduceOnly ? quantity : quantity.negate();
        ObjectNode body = objectMapper.createObjectNode();
        body.put( "contract", plan.venueSymbol() );
        body.put( "size", size.setScale( 0, RoundingMode.DOWN ).intValueExact() );
        body.put( "price", "0" );
        body.put( "tif", "ioc" );
        body.put( "reduce_only", reduceOnly );
        String bodyJson = objectMapper.writeValueAsString( body );

        long timestamp = System.currentTimeMillis() / 1000L;
        String requestPath = "/futures/usdt/orders";
        String bodyHash = sha512Hex( bodyJson );
        String signatureString = "POST\n/api/v4" + requestPath + "\n\n" + bodyHash + "\n" + timestamp;
        String sign = HmacSigner.hmacSha512( credential( "gate", "secret-key" ), signatureString );

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( baseUrl + requestPath ) )
                                         .timeout( REQUEST_TIMEOUT )
                                         .header( "Content-Type", "application/json" )
                                         .header( "KEY", credential( "gate", "api-key" ) )
                                         .header( "Timestamp", String.valueOf( timestamp ) )
                                         .header( "SIGN", sign )
                                         .POST( HttpRequest.BodyPublishers.ofString( bodyJson ) )
                                         .build();
        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        JsonNode root = parseBody( response.body() );
        if( response.statusCode() >= 300 )
        {
            return rejected( plan, intent, quantity, attemptedAt, "Gate order rejected: " + root.path( "message" ).asText( response.body() ) );
        }
        String status = root.path( "status" ).asText();
        String finishAs = root.path( "finish_as" ).asText();
        return completed(
            plan,
            intent,
            root.path( "size" ).decimalValue().abs(),
            "finished".equalsIgnoreCase( status ) && "filled".equalsIgnoreCase( finishAs )
            ? OrderAttemptStatus.FILLED
            : OrderAttemptStatus.ACKNOWLEDGED,
            root.path( "id" ).asText(),
            attemptedAt,
            secondsInstant( root.path( "create_time" ).asLong( 0L ), attemptedAt ),
            decimalOrNull( root.path( "fill_price" ).asText( null ) ),
            root.path( "size" ).decimalValue().abs(),
            decimalOrNull( root.path( "fee" ).asText( null ) )
        );
    }

    private String liveGateFailure( EngineExecutionPlan plan, String venue, OrderIntent intent, boolean reduceOnly, Instant now )
    {
        if( plan == null )
        {
            return "Engine execution plan must be present.";
        }
        if( !boolProperty( "engine.live-order-enabled", false ) )
        {
            return "Engine live order adapters are still guarded. Set ENGINE_LIVE_ORDER_ENABLED=true before live submission.";
        }
        if( boolProperty( "engine.kill-switch-enabled", true ) )
        {
            return "Engine kill switch is enabled. Set ENGINE_KILL_SWITCH_ENABLED=false before live submission.";
        }
        if( !enabledVenues().contains( venue ) )
        {
            return "Venue " + venue + " is not enabled in ENGINE_LIVE_ENABLED_VENUES.";
        }
        BigDecimal maxNotional = decimalProperty( "engine.max-notional-usd", BigDecimal.valueOf( 25 ) );
        if( plan.notionalUsd() == null || plan.notionalUsd().compareTo( maxNotional ) > 0 )
        {
            return "Live notionalUsd must be <= " + plain( maxNotional ) + ".";
        }
        if( !reduceOnly && ( plan.entryAttemptCount() == null || plan.entryAttemptCount() != 1 ) )
        {
            return "Live v1 entryAttemptCount must be 1.";
        }
        if( !reduceOnly && intent.side() != TradeSide.SHORT )
        {
            return "Live v1 entry side must be SHORT.";
        }
        if( plan.venueSymbol() == null || plan.venueSymbol().isBlank()
            || plan.minOrderQty() == null
            || plan.qtyStep() == null
            || plan.minNotionalValue() == null )
        {
            return "Instrument metadata must be present before live submission.";
        }
        if( stale( plan.metadataLastSyncedAt(), minutesProperty( "engine.metadata-max-age-minutes", 240L ), now ) )
        {
            return "Instrument metadata is stale before live submission.";
        }
        if( stale( plan.latencySampledAt(), minutesProperty( "engine.latency-max-age-minutes", 1440L ), now ) )
        {
            return "Latency profile is stale before live submission.";
        }
        return null;
    }

    private BigDecimal orderQuantity( EngineExecutionPlan plan, OrderIntent intent, boolean reduceOnly )
    {
        BigDecimal raw = reduceOnly ? intent.quantity() : plan.notionalUsd().divide( priceReference( plan ), 16, RoundingMode.DOWN );
        BigDecimal rounded = roundDownToStep( raw, plan.qtyStep() );
        if( rounded.compareTo( plan.minOrderQty() ) < 0 )
        {
            throw new IllegalArgumentException( "Order quantity is below instrument minOrderQty." );
        }
        if( !reduceOnly && rounded.multiply( priceReference( plan ) ).compareTo( plan.minNotionalValue() ) < 0 )
        {
            throw new IllegalArgumentException( "Order notional is below instrument minNotionalValue." );
        }
        return rounded;
    }

    private BigDecimal priceReference( EngineExecutionPlan plan )
    {
        if( plan.positionEntryPrice() == null || plan.positionEntryPrice().signum() <= 0 )
        {
            throw new IllegalArgumentException( "Price reference is missing for notional-to-quantity conversion." );
        }
        return plan.positionEntryPrice();
    }

    private static BigDecimal roundDownToStep( BigDecimal value, BigDecimal step )
    {
        if( step == null || step.signum() <= 0 )
        {
            return value;
        }
        BigDecimal units = value.divide( step, 0, RoundingMode.DOWN );
        return units.multiply( step ).stripTrailingZeros();
    }

    private OrderAttempt completed(
        EngineExecutionPlan plan,
        OrderIntent intent,
        BigDecimal quantity,
        OrderAttemptStatus status,
        String externalOrderId,
        Instant submittedAt,
        Instant exchangeTimestamp,
        BigDecimal averageFillPrice,
        BigDecimal filledQuantity,
        BigDecimal feeUsd
    )
    {
        return new OrderAttempt(
            null,
            null,
            plan.armedTradeId(),
            null,
            normalizeVenue( plan.venue() ),
            plan.symbol(),
            intent.side(),
            intent.executionType(),
            quantity,
            intent.limitPrice(),
            status,
            externalOrderId,
            null,
            null,
            submittedAt,
            exchangeTimestamp,
            null,
            averageFillPrice,
            filledQuantity,
            feeUsd,
            null,
            null
        );
    }

    private OrderAttempt rejected( EngineExecutionPlan plan, OrderIntent intent, BigDecimal quantity, Instant attemptedAt, String reason )
    {
        return new OrderAttempt(
            null,
            null,
            plan.armedTradeId(),
            null,
            normalizeVenue( plan.venue() ),
            plan.symbol(),
            intent.side(),
            intent.executionType(),
            quantity,
            intent.limitPrice(),
            OrderAttemptStatus.REJECTED,
            null,
            null,
            null,
            attemptedAt,
            null,
            reason,
            null,
            null,
            null,
            null,
            null
        );
    }

    private OrderAttempt failed(
        EngineExecutionPlan plan,
        String venue,
        String symbol,
        OrderIntent intent,
        Instant attemptedAt,
        String reason
    )
    {
        return new OrderAttempt(
            null,
            null,
            plan == null ? 0L : plan.armedTradeId(),
            null,
            venue,
            symbol,
            intent.side(),
            intent.executionType(),
            intent.quantity(),
            intent.limitPrice(),
            OrderAttemptStatus.FAILED,
            null,
            null,
            null,
            attemptedAt,
            null,
            reason,
            null,
            null,
            null,
            null,
            null
        );
    }

    private String missingCredentialsReason( String venue )
    {
        String apiKey = credential( venue, "api-key" );
        String secretKey = credential( venue, "secret-key" );
        if( apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank() )
        {
            return "Missing engine credentials for " + venue
                   + ". Configure engine.credentials." + venue + ".api-key and engine.credentials." + venue + ".secret-key.";
        }
        if( requiresPassphrase( venue ) )
        {
            String passphrase = credential( venue, "passphrase" );
            if( passphrase == null || passphrase.isBlank() )
            {
                return "Missing engine passphrase for " + venue + ". Configure engine.credentials." + venue + ".passphrase.";
            }
        }
        return null;
    }

    private String baseUrl( String venue )
    {
        String mode = property( "engine.trading-venue-access-mode", "testnet" ).trim().toLowerCase( Locale.ROOT );
        String property = "engine." + venue + "." + mode + "-base-url";
        String fallback = switch( venue + ":" + mode )
        {
            case "bybit:production" -> "https://api.bybit.com";
            case "gate:production" -> "https://fx-api.gateio.ws/api/v4";
            case "gate:testnet" -> "https://fx-api-testnet.gateio.ws/api/v4";
            default -> "https://api-testnet.bybit.com";
        };
        String url = property( property, fallback );
        return url.endsWith( "/" ) ? url.substring( 0, url.length() - 1 ) : url;
    }

    private Set<String> enabledVenues()
    {
        return Arrays.stream( property( "engine.live-enabled-venues", "bybit,gate" ).split( "," ) )
                     .map( LiveExchangeExecutionPort::normalizeVenue )
                     .filter( venue -> !venue.isBlank() )
                     .collect( Collectors.toUnmodifiableSet() );
    }

    private String credential( String venue, String name )
    {
        return environment.getProperty( "engine.credentials." + venue + "." + name );
    }

    private String property( String name, String fallback )
    {
        return environment.getProperty( name, fallback );
    }

    private boolean boolProperty( String name, boolean fallback )
    {
        return Boolean.parseBoolean( environment.getProperty( name, String.valueOf( fallback ) ) );
    }

    private long minutesProperty( String name, long fallback )
    {
        return Long.parseLong( environment.getProperty( name, String.valueOf( fallback ) ) );
    }

    private BigDecimal decimalProperty( String name, BigDecimal fallback )
    {
        String value = environment.getProperty( name );
        return value == null || value.isBlank() ? fallback : new BigDecimal( value.trim() );
    }

    private boolean stale( Instant sampledAt, long maxAgeMinutes, Instant now )
    {
        return sampledAt == null || sampledAt.plus( Duration.ofMinutes( maxAgeMinutes ) ).isBefore( now );
    }

    private JsonNode parseBody( String body ) throws IOException
    {
        return body == null || body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree( body );
    }

    private static String normalizeVenue( String venue )
    {
        return venue == null ? "" : venue.trim().toLowerCase( Locale.ROOT );
    }

    private static boolean requiresPassphrase( String venue )
    {
        return "bitget".equals( venue ) || "okx".equals( venue ) || "kucoin".equals( venue );
    }

    private static String orderLinkId( EngineExecutionPlan plan, boolean reduceOnly )
    {
        return "engine-" + plan.armedTradeId() + "-" + ( reduceOnly ? "exit" : "entry" );
    }

    private static String plain( BigDecimal value )
    {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String encode( String value )
    {
        return URLEncoder.encode( value, StandardCharsets.UTF_8 );
    }

    private static BigDecimal decimalOrNull( String value )
    {
        return value == null || value.isBlank() ? null : new BigDecimal( value );
    }

    private static Instant millisInstant( String value, Instant fallback )
    {
        if( value == null || value.isBlank() )
        {
            return fallback;
        }
        return Instant.ofEpochMilli( Long.parseLong( value ) );
    }

    private static Instant secondsInstant( long value, Instant fallback )
    {
        return value <= 0L ? fallback : Instant.ofEpochSecond( value );
    }

    private static String sha512Hex( String body )
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance( "SHA-512" );
            byte[] hash = digest.digest( body.getBytes( StandardCharsets.UTF_8 ) );
            return HexFormat.of().formatHex( hash );
        }
        catch( Exception e )
        {
            throw new IllegalStateException( "Failed to calculate SHA-512 body hash", e );
        }
    }
}
