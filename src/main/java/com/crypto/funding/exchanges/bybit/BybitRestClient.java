package com.crypto.funding.exchanges.bybit;

import com.crypto.funding.exchanges.AbstractRestClient;
import com.crypto.funding.exchanges.Exchange;
import com.crypto.funding.exchanges.ExchangeRestClient;
import com.crypto.funding.trading.*;
import com.crypto.funding.utills.SymbolMapper;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class BybitRestClient extends AbstractRestClient implements ExchangeRestClient, ExchangeTradingClient
{

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public BybitRestClient(
        @Value("${trading.bybit.base-url}") String baseUrl,
        @Value("${trading.bybit.api-key:}") String apiKey,
        @Value("${trading.bybit.secret-key:}") String secretKey,
        @Value("${trading.bybit.recv-window:5000}") long recvWindow
    )
    {
        super( baseUrl, apiKey, secretKey, recvWindow );
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

        return new TestOrderResult(
            name(),
            orderId,
            cmd.symbolUnified(),
            cmd.side(),
            cmd.type(),
            qty,
            price,
            status,
            System.currentTimeMillis()
        );
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
        String url = "https://api.bybit.com/v5/market/tickers?category=linear&symbol=" + bybitSymbol;

        HttpRequest req = HttpRequest.newBuilder()
                                     .uri(URI.create(url))
                                     .timeout(Duration.ofSeconds(5))
                                     .GET()
                                     .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        BybitResponse dto = mapper.readValue(resp.body(), BybitResponse.class);

        if (dto.result == null || dto.result.list == null || dto.result.list.length == 0) {
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
        String url = getBaseUrl() + "/v5/market/instruments-info?category=linear&symbol=" + symbol;

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                                     .GET()
                                     .build();

        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            BybitInstrumentsResponse dto = mapper.readValue(resp.body(), BybitInstrumentsResponse.class);

            if (dto.result == null || dto.result.list == null || dto.result.list.length == 0) {
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class BybitResponse {
        public BybitResult result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class BybitResult {
        public BybitTicker[] list;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class BybitTicker {
        public String symbol;
        public String fundingRate;        // "0.0001"
        public String indexPrice;
        public String nextFundingTime;    // "1730467200000"
        public String fundingIntervalHour;// "8"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class BybitInstrumentsResponse {
        public BybitInstrumentsResult result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class BybitInstrumentsResult {
        public BybitInstrument[] list;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class BybitInstrument {
        public String symbol;
        public LotSizeFilter lotSizeFilter;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LotSizeFilter {
        public String minOrderQty;       // "0.001"
        public String qtyStep;           // "0.001"
        public String minNotionalValue;  // sometimes exists, depends on market
    }


}
