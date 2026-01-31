package com.crypto.funding.exchanges.bybit;

import com.crypto.funding.exchanges.AbstractRestClient;
import com.crypto.funding.exchanges.Exchange;
import com.crypto.funding.exchanges.ExchangeRestClient;
import com.crypto.funding.trading.*;
import com.crypto.funding.utills.SymbolMapper;
import com.crypto.funding.watchlist.FundingInfo;
import com.crypto.funding.watchlist.SymbolRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class BybitRestClient extends AbstractRestClient implements ExchangeRestClient, ExchangeTradingClient
{

    private final ObjectMapper mapper = new ObjectMapper();
    private final BybitFeignClient feignClient;

    public BybitRestClient(
        @Value("${trading.bybit.base-url:https://api-testnet.bybit.com}") String baseUrl,
        @Value("${trading.bybit.api-key:${BYBIT_API_KEY:${BYBIT_TESTNET_API_KEY:${BYBIT_PROD_API_KEY:}}}}") String apiKey,
        @Value("${trading.bybit.secret-key:${BYBIT_SECRET_KEY:${BYBIT_TESTNET_SECRET_KEY:${BYBIT_PROD_SECRET_KEY:}}}}") String secretKey,
        @Value("${trading.bybit.recv-window:5000}") long recvWindow,
        BybitFeignClient feignClient
    )
    {
        super( baseUrl, apiKey, secretKey, recvWindow );
        this.feignClient = feignClient;
    }

    @Override
    public String name() {
        return "bybit";
    }

    @Override
    public @NonNull TestOrderResult createOrderResult( PlaceTestOrderCommand cmd, HttpResponse<String> response ) throws JsonProcessingException
    {
        JsonNode root = mapper.readTree( response.body());
        int retCode = root.path("retCode").asInt(-1);
        if (retCode != 0) {
            throw new RuntimeException("Bybit retCode=" + retCode +
                                       " msg=" + root.path("retMsg").asText());
        }

        JsonNode result = root.path("result");
        String orderId = result.path("orderId").asText("UNKNOWN");
        String status = result.path("orderStatus").asText("NEW");

        BigDecimal qty = cmd.quantity();
        BigDecimal price = cmd.price() != null
                           ? cmd.price()
                           : new BigDecimal(result.path("avgPrice").asText(
            result.path("price").asText("0")
        ));

        Long exchangeTs = parseExchangeTimestamp(result);

        return new TestOrderResult(
            name(),
            orderId,
            cmd.symbolUnified(),
            cmd.side(),
            cmd.type(),
            qty,
            price,
            status,
            System.currentTimeMillis(),
            exchangeTs,
            exchangeTs == null ? OrderTimestampSource.UNKNOWN : OrderTimestampSource.RESPONSE_BODY
        );
    }

    private static Long parseExchangeTimestamp(JsonNode result)
    {
        // Bybit v5: createdTime or updatedTime in ms, strings
        String created = result.path("createdTime").asText(null);
        if (created != null && !created.isBlank()) {
            try {
                return Long.parseLong(created);
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    public HttpRequest createHttpRequest( PlaceTestOrderCommand cmd ) throws JsonProcessingException
    {
        Map<String, Object> body = new HashMap<>();
        body.put("category", "linear"); // или "inverse" / "option" — зависит от того, что ты используешь
        body.put("symbol", SymbolMapper.toExchange( cmd.symbolUnified() ));
        body.put("side", cmd.side() == OrderSide.BUY ? "Buy" : "Sell");
        body.put("orderType", cmd.type() == OrderType.MARKET ? "Market" : "Limit");
        body.put("qty", cmd.quantity().toPlainString());
        if ( cmd.type() == OrderType.LIMIT && cmd.price() != null) {
            body.put("price", cmd.price().toPlainString());
            body.put("timeInForce", "GoodTillCancel");
        }

        String bodyJson = mapper.writeValueAsString(body);
        long timestamp = System.currentTimeMillis();
        String signPayload = timestamp + getApiKey() + getRecvWindow() + bodyJson;
        String sign = HmacSigner.hmacSha256(getSecretKey(), signPayload);

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(getBaseUrl() + "/v5/order/create"))
                                         .timeout(Duration.ofSeconds(5))
                                         .header("Content-Type", "application/json")
                                         .header("X-BAPI-API-KEY", getApiKey())
                                         .header("X-BAPI-TIMESTAMP", String.valueOf( timestamp ))
                                         .header("X-BAPI-RECV-WINDOW", String.valueOf(getRecvWindow()))
                                         .header("X-BAPI-SIGN", sign)
                                         .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                                         .build();
        return request;
    }

    @Override
    public String exchangeName()
    {
        return Exchange.BYBIT.id();
    }

    @Override
    public FundingInfo fetchFunding(String unifiedSymbol) throws Exception {
        // unifiedSymbol "BTC/USDT" -> "BTCUSDT" для Bybit linear перпетуалов
        String bybitSymbol = SymbolMapper.toExchange(unifiedSymbol); // "BTCUSDT"
        BybitMarketTickersResponse dto = feignClient.getTickers( "linear", bybitSymbol );

        if (dto == null || dto.result == null || dto.result.list == null || dto.result.list.length == 0) {
            throw new IllegalStateException("Empty funding data for " + unifiedSymbol + " from Bybit");
        }

        BybitTicker t = dto.result.list[0];

        double fundingRatePct = Double.parseDouble(t.fundingRate) * 100.0;
        Instant nextFundingAt = Instant.ofEpochMilli(Long.parseLong(t.nextFundingTime));
        long secondsToFunding = Duration.between(Instant.now(), nextFundingAt).getSeconds();
        BigDecimal price = BigDecimal.valueOf( Double.parseDouble( t.indexPrice  ));

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
    public SymbolRules fetchRules( String symbol )
    {
        try {
            BybitInstrumentsResponse dto = feignClient.getInstruments( "linear", symbol );

            if (dto == null || dto.result == null || dto.result.list == null || dto.result.list.length == 0) {
                throw new IllegalStateException("Empty instruments-info for " + symbol + " from Bybit");
            }

            BybitInstrument i = dto.result.list[0];
            if (i.lotSizeFilter == null) {
                throw new IllegalStateException("No lotSizeFilter for " + symbol + " from Bybit");
            }

            BigDecimal minQty = new BigDecimal(i.lotSizeFilter.minOrderQty);
            BigDecimal step = new BigDecimal(i.lotSizeFilter.qtyStep);
            BigDecimal minNotional = (i.lotSizeFilter.minNotionalValue == null || i.lotSizeFilter.minNotionalValue.isBlank())
                                     ? null
                                     : new BigDecimal(i.lotSizeFilter.minNotionalValue);

            return new SymbolRules(minQty, step, minNotional);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch symbol rules from Bybit for " + symbol, e);
        }
    }

    @Override
    public Long fetchOrderTimestamp(String unifiedSymbol, String exchangeOrderId) throws Exception
    {
        if (exchangeOrderId == null || exchangeOrderId.isBlank()) {
            return null;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("category", "linear");
        body.put("orderId", exchangeOrderId);
        String bodyJson = mapper.writeValueAsString(body);

        long timestamp = System.currentTimeMillis();
        String signPayload = timestamp + getApiKey() + getRecvWindow() + bodyJson;
        String sign = HmacSigner.hmacSha256(getSecretKey(), signPayload);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(getBaseUrl() + "/v5/order/list"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .header("X-BAPI-API-KEY", getApiKey())
            .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
            .header("X-BAPI-RECV-WINDOW", String.valueOf(getRecvWindow()))
            .header("X-BAPI-SIGN", sign)
            .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        validateResponse(response);

        JsonNode root = mapper.readTree(response.body());
        if (root.path("retCode").asInt(-1) != 0) {
            throw new RuntimeException("Bybit list retCode=" + root.path("retCode").asInt() + " msg=" + root.path("retMsg").asText());
        }

        JsonNode list = root.path("result").path("list");
        if (list.isArray() && list.size() > 0) {
            JsonNode first = list.get(0);
            String created = first.path("createdTime").asText(null);
            if (created != null && !created.isBlank()) {
                try {
                    return Long.parseLong(created);
                } catch (NumberFormatException ignore) {
                    return null;
                }
            }
        }
        return null;
    }

    @Override
    public void cancelTestOrder(String unifiedSymbol, String exchangeOrderId) throws Exception {
        if (exchangeOrderId == null || exchangeOrderId.isBlank()) {
            return; // nothing to cancel
        }

        Map<String, Object> body = new HashMap<>();
        body.put("category", "linear");
        body.put("symbol", SymbolMapper.toExchange(unifiedSymbol));
        body.put("orderId", exchangeOrderId);

        String bodyJson = mapper.writeValueAsString(body);
        long timestamp = System.currentTimeMillis();
        String signPayload = timestamp + getApiKey() + getRecvWindow() + bodyJson;
        String sign = HmacSigner.hmacSha256(getSecretKey(), signPayload);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(getBaseUrl() + "/v5/order/cancel"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .header("X-BAPI-API-KEY", getApiKey())
            .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
            .header("X-BAPI-RECV-WINDOW", String.valueOf(getRecvWindow()))
            .header("X-BAPI-SIGN", sign)
            .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(response.body());
        int retCode = root.path("retCode").asInt(-1);
        if (retCode != 0) {
            throw new RuntimeException("Bybit cancel retCode=" + retCode +
                " msg=" + root.path("retMsg").asText());
        }
    }
}
