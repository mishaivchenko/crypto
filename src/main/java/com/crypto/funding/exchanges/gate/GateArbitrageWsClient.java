package com.crypto.funding.exchanges.gate;

import com.crypto.funding.exchanges.AbstractWsClient;

import java.net.URI;
import java.util.*;

import com.crypto.funding.market.MarketCache;
import com.crypto.funding.market.model.Quote;
import com.crypto.funding.utills.SymbolMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Service;

@Service
public class GateArbitrageWsClient extends AbstractWsClient
{
    private final MarketCache cache;

    public GateArbitrageWsClient( MarketCache cache )
    {
        this.cache = cache;
    }

    @Override
    public String name()
    {
        return "gate";
    }

    @Override
    protected URI endpoint()
    {
        return URI.create( "wss://fx-ws.gateio.ws/v4/ws/usdt" );
    }

    @Override
    protected String buildSubscribeMessage(List<String> unifiedSymbols) {
        List<String> contracts = new ArrayList<>();
        for (String u : unifiedSymbols) {
            contracts.add(SymbolMapper.toGate(u)); // "BTC_USDT"
        }
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("time", System.currentTimeMillis() / 1000);
        sub.put("channel", "futures.book_ticker");
        sub.put("event", "subscribe");
        sub.put("payload", contracts);

        try { return mapper.writeValueAsString(sub); }
        catch (Exception e) { return null; }
    }

    @Override
    protected void handleTextMessage(String text) throws Exception {

        // {"event":"subscribe","error":{...}}
        if (text.contains("\"event\":\"subscribe\"")) {
            if (text.contains("\"error\"")) {
                log.warn("[gate] subscribe failed: {}", text);
            }
            return;
        }

        // игнорим подтверждения подписки и ошибки
        if (text == null || text.isEmpty()) return;
        char c0 = text.charAt(0);
        if (c0 != '{' && c0 != '[') return;

        if (text.contains("\"event\":\"subscribe\"")) return;
        if (text.contains("\"error\"")) {
            log.error( "[gate] subscribe failed: {}", text );
        }

        var root = mapper.readTree(text);
        if (root == null) return;

        String channel = root.path("channel").asText("");
        if (!"futures.book_ticker".equals(channel)) return;

        var result = root.get("result");
        if (result == null || result.isNull()) return;

        String contract = result.path("s").asText(null); // контракт, пример: "BTC_USDT"
        if (contract == null) return;
        String unified = SymbolMapper.toUnified(contract); // -> "BTC/USDT"

        double bid = d(result.path("b").asText());
        double ask = d(result.path("a").asText());

        if (bid > 0 && ask > 0) {
            cache.put(name(), unified, new Quote(bid, ask, System.nanoTime()));
        }
    }

    private static double d(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    @JsonIgnoreProperties( ignoreUnknown = true )
    static class Msg
    {
        public String channel;
        public Data result;
    }

    @JsonIgnoreProperties( ignoreUnknown = true )
    static class Data
    {
        public String contract;
        public String bid;
        public String ask;
    }
}
