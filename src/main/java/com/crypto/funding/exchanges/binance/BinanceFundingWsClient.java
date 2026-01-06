package com.crypto.funding.exchanges.binance;

import com.crypto.funding.exchanges.AbstractWsClient;
import com.crypto.funding.utills.SymbolMapper;
import com.crypto.funding.watchlist.FundingInfo;
import com.crypto.funding.watchlist.FundingWatchlistService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BinanceFundingWsClient extends AbstractWsClient
{
    private final FundingWatchlistService fundingWatchlistService;

    public BinanceFundingWsClient( FundingWatchlistService fundingWatchlistService )
    {
        this.fundingWatchlistService = fundingWatchlistService;
    }

    @Override
    public String name()
    {
        return "binance";
    }

    @Override protected URI endpoint() {
        // мультистрим endpoint; подписки будем отправлять командой
        return URI.create("wss://fstream.binance.com/stream");
    }

    @Override protected String buildSubscribeMessage( List<String> unifiedSymbols) {
        // streams: sdusdt@bookTicker, etc.
        List<String> streams = new ArrayList<>();
        for (String u : unifiedSymbols) {
            streams.add( SymbolMapper.toBinance(u).toLowerCase() + "@markPrice");
        }
        Map<String,Object> sub = Map.of(
            "method","SUBSCRIBE",
            "params", streams,
            "id", System.currentTimeMillis()
        );
        try { return mapper.writeValueAsString(sub); } catch (Exception e) { return null; }
    }

    @Override
    protected void handleTextMessage( String text ) throws Exception
    {
        var w = mapper.readValue(text, Wrapper.class);
        if (w == null || w.data == null) return;

        // w.data.s = "BTCUSDT"; w.data.r = "0.00010000"; w.data.T = 1730467200000
        String unified = SymbolMapper.toUnified(w.data.s);
        double ratePct = parseD(w.data.r) * 100d;
        Instant next = Instant.ofEpochMilli(w.data.T);
        long sec = Math.max(0, Duration.between(Instant.now(), next).toSeconds());

        if (ratePct != 0d && next.toEpochMilli() > 0) {
            fundingWatchlistService.updateFunding(new FundingInfo(name(), unified, ratePct, next, sec));
        }
    }

    private static double parseD(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0d; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Wrapper { public Data data; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Data { public String s; public String r; public long T; }
}
