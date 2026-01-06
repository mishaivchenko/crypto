package com.crypto.funding.exchanges.gate;

import com.crypto.funding.exchanges.ExchangeRestClient;
import com.crypto.funding.trading.*;
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
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class GateRestClient implements ExchangeRestClient, ExchangeTradingClient
{

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${trading.gate.base-url}")
    private String baseUrl;

    @Value("${trading.gate.api-key:}")
    private String apiKey;

    @Value("${trading.gate.secret-key:}")
    private String secretKey;

    @Override
    public String name() {
        return "gate";
    }

    @Override
    public TestOrderResult placeTestOrder( PlaceTestOrderCommand cmd ) throws Exception
    {
        ensureConfigured();

        String contract = cmd.symbolUnified(); // для Gate обычно "BTC_USDT"
        String path = "/futures/usdt/orders";
        String method = "POST";
        String queryString = ""; // без query-параметров — всё в body

        Map<String, Object> body = new HashMap<>();
        body.put("contract", contract);
        // size > 0 — long, < 0 — short
        int size = cmd.side() == OrderSide.BUY
                   ? cmd.quantity().intValue()
                   : -cmd.quantity().intValue();
        body.put("size", size);
        if ( cmd.type() == OrderType.LIMIT && cmd.price() != null) {
            body.put("price", cmd.price().toPlainString());
            body.put("tif", "gtc");
        }

        String bodyJson = mapper.writeValueAsString(body);
        String bodyHash = sha512Hex(bodyJson);

        long timestamp = System.currentTimeMillis() / 1000L;

        String signatureString = method + "\n" +
                                 "/api/v4" + path + "\n" +
                                 queryString + "\n" +
                                 bodyHash + "\n" +
                                 timestamp;

        String sign = HmacSigner.hmacSha512(secretKey, signatureString);

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(baseUrl + path))
                                         .timeout(Duration.ofSeconds(5))
                                         .header("Content-Type", "application/json")
                                         .header("KEY", apiKey)
                                         .header("Timestamp", String.valueOf(timestamp))
                                         .header("SIGN", sign)
                                         .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                                         .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 300) {
            throw new RuntimeException("Gate testnet order failed: " + response.statusCode() +
                                       " body=" + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        String orderId = root.path("id").asText("UNKNOWN");
        String status = root.path("status").asText("open");

        BigDecimal qty = cmd.quantity();
        BigDecimal price = cmd.price() != null
                           ? cmd.price()
                           : new BigDecimal(root.path("price").asText("0"));

        return new TestOrderResult(
            name(),
            orderId,
            contract,
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
            throw new IllegalStateException("Gate testnet API key/secret not configured");
        }
    }

    private static String sha512Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        byte[] hash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public FundingInfo fetchFunding(String unifiedSymbol) throws Exception {
        // Gate хочет формат BTC_USDT
        // unifiedSymbol у нас "BTC/USDT"
        String base = unifiedSymbol.split("/")[0];
        String gateContract = base + "_USDT";

        String url = "https://api.gateio.ws/api/v4/futures/usdt/contracts/" + gateContract;

        HttpRequest req = HttpRequest.newBuilder()
                                     .uri(URI.create(url))
                                     .timeout(Duration.ofSeconds(5))
                                     .header("Accept", "application/json")
                                     .GET()
                                     .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        GateContract dto = mapper.readValue(resp.body(), GateContract.class);

        double fundingRatePct = Double.parseDouble(dto.funding_rate) * 100.0;

        // Gate возвращает funding_next_apply как юникс-время следующего начисления.
        // В публичных описаниях оно идёт в секундах Unix.  [oai_citation:5‡pkg.go.dev](https://pkg.go.dev/github.com/xyths/hs/exchange?utm_source=chatgpt.com)
        Instant nextFundingAt = Instant.ofEpochSecond((long) dto.funding_next_apply);

        long secondsToFunding = java.time.Duration.between(Instant.now(), nextFundingAt).getSeconds();

        return new FundingInfo(
            name(),
            unifiedSymbol,
            fundingRatePct,
            nextFundingAt,
            secondsToFunding
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GateContract {
        public String name;                // "BTC_USDT"
        public String funding_rate;        // "0.0001"
        public double funding_next_apply;  // unix ts (sec)
        public int funding_interval;       // seconds between fundings, e.g. 28800
    }
}
