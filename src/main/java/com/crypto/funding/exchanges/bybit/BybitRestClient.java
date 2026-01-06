package com.crypto.funding.exchanges.bybit;

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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class BybitRestClient implements ExchangeRestClient, ExchangeTradingClient
{

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${trading.bybit.base-url}")
    private String baseUrl;

    @Value("${trading.bybit.api-key:}")
    private String apiKey;

    @Value("${trading.bybit.secret-key:}")
    private String secretKey;

    @Value("${trading.bybit.recv-window:5000}")
    private long recvWindow;

    @Override
    public String name() {
        return "bybit";
    }

    @Override
    public TestOrderResult placeTestOrder( PlaceTestOrderCommand cmd ) throws Exception
    {
        ensureConfigured();

        String symbol = cmd.symbolUnified(); // пока считаем, что уже BYBIT-формат
        long timestamp = System.currentTimeMillis();

        Map<String, Object> body = new HashMap<>();
        body.put("category", "linear"); // или "inverse" / "option" — зависит от того, что ты используешь
        body.put("symbol", symbol);
        body.put("side", cmd.side() == OrderSide.BUY ? "Buy" : "Sell");
        body.put("orderType", cmd.type() == OrderType.MARKET ? "Market" : "Limit");
        body.put("qty", cmd.quantity().toPlainString());
        if (cmd.type() == OrderType.LIMIT && cmd.price() != null) {
            body.put("price", cmd.price().toPlainString());
            body.put("timeInForce", "GoodTillCancel");
        }

        String bodyJson = mapper.writeValueAsString(body);
        String signPayload = timestamp + apiKey + recvWindow + bodyJson;
        String sign = HmacSigner.hmacSha256(secretKey, signPayload);

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(baseUrl + "/v5/order/create"))
                                         .timeout(Duration.ofSeconds(5))
                                         .header("Content-Type", "application/json")
                                         .header("X-BAPI-API-KEY", apiKey)
                                         .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                                         .header("X-BAPI-RECV-WINDOW", String.valueOf(recvWindow))
                                         .header("X-BAPI-SIGN", sign)
                                         .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                                         .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new RuntimeException("Bybit testnet order failed: " + response.statusCode() +
                                       " body=" + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
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
            symbol,
            cmd.side(),
            cmd.type(),
            qty,
            price,
            status,
            System.currentTimeMillis()
        );

    }

    private void ensureConfigured() {
        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Bybit testnet API key/secret not configured");
        }
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

        return new FundingInfo(
            name(),
            unifiedSymbol,
            fundingRatePct,
            nextFundingAt,
            secondsToFunding
        );
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
        public String nextFundingTime;    // "1730467200000"
        public String fundingIntervalHour;// "8"
    }
}
