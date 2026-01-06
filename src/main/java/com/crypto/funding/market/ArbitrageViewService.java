package com.crypto.funding.market;

import com.crypto.funding.market.MarketCache;
import com.crypto.funding.market.model.Quote;
import com.crypto.funding.watchlist.ArbitrageWatchlistService;
import com.crypto.funding.utills.SymbolMapper; // если SymbolMapper в другом пакете — поправь import
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Готовит “вид” по арбитражу: котировки по биржам + лучший buy/sell + спред.
 * Ничего не пишет — только читает watchlist и кэш котировок.
 */
@Service
public class ArbitrageViewService {

    public record ExchangeQuote(String exchange, double bid, double ask, long tsNanos) {}

    public record ArbitrageAnalysis(
        String symbol,                        // "SD/USDT"
        Instant expireAt,
        Map<String, ExchangeQuote> quotes,    // exchange -> bid/ask
        String bestBuyExchange,
        double bestBuyPrice,
        String bestSellExchange,
        double bestSellPrice,
        double spreadPct                      // ((sell - buy)/buy)*100
    ) {}

    private final ArbitrageWatchlistService arbitrageWatchlist;
    private final MarketCache marketCache;

    public ArbitrageViewService(ArbitrageWatchlistService arbitrageWatchlist,
        MarketCache marketCache) {
        this.arbitrageWatchlist = arbitrageWatchlist;
        this.marketCache = marketCache;
    }

    public List<ArbitrageAnalysis> buildView() {
        List<ArbitrageAnalysis> out = new ArrayList<>();

        // пробегаемся по текущему арбитражному watchlist-у
        for (ArbitrageWatchlistService.Item item : arbitrageWatchlist.all()) {
            String unified = SymbolMapper.toUnified(item.symbol());

            // забираем все доступные котировки из кэша
            var raw = marketCache.getAll(unified); // Map<exchange, Quote>
            Map<String, ExchangeQuote> quotes = new LinkedHashMap<>();

            double bestBuy = Double.POSITIVE_INFINITY;
            String bestBuyEx = null;

            double bestSell = Double.NEGATIVE_INFINITY;
            String bestSellEx = null;

            for (Map.Entry<String, Quote> e : raw.entrySet()) {
                String ex = e.getKey();
                var q = e.getValue();
                if (q == null || q.bid() <= 0 || q.ask() <= 0) continue;

                quotes.put(ex, new ExchangeQuote(ex, q.bid(), q.ask(), q.tsNanos()));

                // лучшее место КУПИТЬ — минимальный ask
                if (q.ask() < bestBuy) {
                    bestBuy = q.ask();
                    bestBuyEx = ex;
                }
                // лучшее место ПРОДАТЬ — максимальный bid
                if (q.bid() > bestSell) {
                    bestSell = q.bid();
                    bestSellEx = ex;
                }
            }

            double spread = 0.0;
            if (bestBuy != Double.POSITIVE_INFINITY && bestSell != Double.NEGATIVE_INFINITY && bestBuy > 0) {
                spread = ((bestSell - bestBuy) / bestBuy) * 100.0;
            }

            out.add(new ArbitrageAnalysis(
                unified,
                item.expireAt(),
                quotes,
                bestBuyEx, (bestBuy == Double.POSITIVE_INFINITY ? 0.0 : bestBuy),
                bestSellEx, (bestSell == Double.NEGATIVE_INFINITY ? 0.0 : bestSell),
                spread
            ));
        }

        return out;
    }
}
