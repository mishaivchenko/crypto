package com.crypto.funding.exchanges.bybit;

import com.crypto.funding.exchanges.AbstractWsClient;
import com.crypto.funding.market.MarketCache;
import com.crypto.funding.market.model.Quote;
import com.crypto.funding.utills.SymbolMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BybitArbitrageWsClient extends AbstractWsClient
{
    private final MarketCache cache;

    public BybitArbitrageWsClient( MarketCache cache )
    {
        this.cache = cache;
    }

    @Override
    public String name()
    {
        return "bybit";
    }

    @Override
    protected URI endpoint()
    {
        return URI.create( "wss://stream.bybit.com/v5/public/linear" );
    }

    @Override
    protected String buildSubscribeMessage( List<String> unifiedSymbols )
    {
        List<String> args = new ArrayList<>();
        for (String u : unifiedSymbols) {
            args.add("tickers." + SymbolMapper.toBybit(u)); // "tickers.SDUSDT"
        }
        Map<String, Object> sub = Map.of(
            "op", "subscribe",
            "args", args
        );
        try {
            return mapper.writeValueAsString(sub);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void handleTextMessage(String text) throws Exception {

        // {"op":"subscribe","success":false,"ret_msg":"error: handler not found,topic:..."}
        if ( text.contains( "\"op\":\"subscribe\"" ) ) {
            if ( text.contains( "\"success\":false" ) ) {
                log.warn("[bybit] subscribe failed: {}", text);
            }
            return; // это ack, дальше не парсим как market data
        }
        // быстрые фильтры квитанций
        if (text == null || text.isEmpty()) return;
        char c0 = text.charAt(0);
        if (c0 != '{' && c0 != '[') return;
        if (text.indexOf("\"op\":\"subscribe\"") >= 0) return;
        if (text.indexOf("\"success\":true") >= 0 && text.indexOf("\"op\":\"subscribe\"") >= 0) return;

        var root = mapper.readTree(text);
        if (root == null || !root.has("topic")) return;

        String topic = root.get("topic").asText("");
        if (!topic.startsWith("tickers")) return; // и "tickers" и "tickers.BTCUSDT"

        var data = root.get("data");
        if (data == null || data.isNull()) return;

        if (data.isArray()) {
            // snapshot: массив объектов
            for (var n : data) upsertFromNode(n);
        } else if (data.isObject()) {
            // delta: одиночный объект
            upsertFromNode(data);
        }
    }

    private void upsertFromNode(com.fasterxml.jackson.databind.JsonNode n) {
        if (n == null || !n.has("symbol")) return;

        String symbol = n.get("symbol").asText("");
        String unified = SymbolMapper.toUnified(symbol);

        // В tickers есть bid1Price/ask1Price. Иногда прилетает только lastPrice — пропускаем.
        double bid = parseD(n.path("bid1Price").asText(null));
        double ask = parseD(n.path("ask1Price").asText(null));

        if (bid > 0 && ask > 0) {
            cache.put(name(), unified, new Quote(bid, ask, System.nanoTime()));
        }
    }

    private static double parseD(String s) {
        if (s == null) return 0;
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }
}
