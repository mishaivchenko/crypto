package com.crypto.funding.exchanges.binance;

import com.crypto.funding.exchanges.AbstractWsClient;
import com.crypto.funding.market.MarketCache;
import com.crypto.funding.market.model.Quote;
import com.crypto.funding.utills.SymbolMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BinanceArbitrageWsClient extends AbstractWsClient
{
    private final MarketCache cache;

    public BinanceArbitrageWsClient( MarketCache cache) { this.cache = cache; }

    @Override public String name() { return "binance"; }
    @Override protected URI endpoint() {
        // мультистрим endpoint; подписки будем отправлять командой
        return URI.create("wss://fstream.binance.com/stream");
    }

    @Override protected String buildSubscribeMessage( List<String> unifiedSymbols) {
        // streams: sdusdt@bookTicker, etc.
        List<String> streams = new ArrayList<>();
        for (String u : unifiedSymbols) {
            streams.add( SymbolMapper.toBinance(u).toLowerCase() + "@bookTicker");
        }
        Map<String,Object> sub = Map.of(
            "method","SUBSCRIBE",
            "params", streams,
            "id", System.currentTimeMillis()
        );
        try { return mapper.writeValueAsString(sub); } catch (Exception e) { return null; }
    }

    @Override
    protected void handleTextMessage(String text) throws Exception {

        // ack OK у бинанса обычно: {"id":..., "result":null}
        if( text.contains( "\"id\"" ) && text.contains( "\"result\":null" ) )
        {
            return;
        }
        // ack error: {"id":..., "error":{"code":...,"msg":"..."}}
        if( text.contains( "\"id\"" ) && text.contains( "\"error\"" ) )
        {
            log.warn( "[binance] subscribe failed: {}", text );
            return;
        }

        if (text.indexOf("\"stream\"") >= 0) {
            var wr = mapper.readValue(text, Wrapper.class);
            if (wr != null && wr.data != null) upsert(wr.data);
            return;
        }
        // чистый payload без wrapper
        var d = mapper.readValue(text, Data.class);
        upsert(d);
    }

    private void upsert(Data d) {
        if (d == null || d.s == null || d.b == null || d.a == null) return;
        String unified = SymbolMapper.toUnified(d.s);
        double bid = parse(d.b);
        double ask = parse(d.a);
        if (bid > 0 && ask > 0) {
            cache.put(name(), unified, new Quote(bid, ask, System.nanoTime()));
        }
    }
    private static double parse(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Wrapper { public String stream; public Data data; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Data { public String s; public String b; public String a; }
}
