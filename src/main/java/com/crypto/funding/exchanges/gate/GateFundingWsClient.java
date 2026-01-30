package com.crypto.funding.exchanges.gate;

import com.crypto.funding.exchanges.AbstractWsClient;
import com.crypto.funding.utills.SymbolMapper;
import com.crypto.funding.watchlist.FundingInfo;
import com.crypto.funding.watchlist.FundingWatchlistService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GateFundingWsClient extends AbstractWsClient
{
    private final FundingWatchlistService fundingWatchlistService;

    public GateFundingWsClient( FundingWatchlistService fundingWatchlistService )
    {
        this.fundingWatchlistService = fundingWatchlistService;
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
    protected String buildSubscribeMessage( List<String> unifiedSymbols )
    {
        List<String> contracts = new ArrayList<>();
        for( String u : unifiedSymbols )
        {
            contracts.add( SymbolMapper.toGate( u ) ); // "BTC_USDT"
        }
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put( "time", System.currentTimeMillis() / 1000 );
        sub.put( "channel", "futures.tickers" );
        sub.put( "event", "subscribe" );
        sub.put( "payload", contracts );

        try
        {
            return mapper.writeValueAsString( sub );
        }
        catch( Exception e )
        {
            return null;
        }
    }

    @Override
    protected void handleTextMessage(String text) throws Exception {
        JsonNode root = mapper.readTree(text);

        // тот же паттерн, что и в GateArbitrageWsClient
        String channel = root.path("channel").asText();
        if (!"futures.tickers".equals(channel)) {
            return;
        }

        JsonNode result = root.path("result");
        if (result.isMissingNode() || result.isNull()) {
            return;
        }

        if (result.isArray()) {
            for (JsonNode n : result) {
                handleTickerNode(n);
            }
        } else if (result.isObject()) {
            handleTickerNode(result);
        } else {
            // на всякий случай логим странный формат, но не падаем
            log.warn("[gate] unexpected result type: {}", result.getNodeType());
        }
    }

    private void handleTickerNode(JsonNode n) {
        try {
            String contract = n.path("contract").asText(null);   // "KERNEL_USDT"
            if (contract == null || contract.isEmpty()) {
                return;
            }

            // funding_rate может отсутствовать в части апдейтов — это нормально
            String rateStr = n.path("funding_rate").asText(null);
            if (rateStr == null) {
                return;
            }
            double ratePct = parseDouble(rateStr) * 100.0d;

            // funding_next_apply — unix time; на Gate обычно в секундах
            long raw = n.path("funding_next_apply").asLong(0L);
//            if (raw <= 0L) {
//                return;
//            }

            // если вдруг пришло в миллисекундах (очень большое число) — делим
//            long seconds = raw > 1_000_000_000_000L ? raw / 1000L : raw;
//            Instant next = Instant.ofEpochSecond(seconds);
//            long secLeft = Math.max(0L, Duration.between(Instant.now(), next).getSeconds());

            String unified = SymbolMapper.toUnified(contract);   // "KERNEL_USDT" -> "KERNEL/USDT"

            fundingWatchlistService.updateFunding(
                new FundingInfo(
                    name(),        // "gate"
                    unified,
                    ratePct,
                    null,
                    0L,
                    BigDecimal.ZERO
                )
            );
        } catch (Exception e) {
            // важно: не роняем поток, просто лог и дальше живём
            String preview = n.toString();
            if (preview.length() > 300) {
                preview = preview.substring(0, 300) + "...";
            }
            log.error("[gate] funding parse err (node preview): {}", preview, e);
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0d;
        }
    }
}
