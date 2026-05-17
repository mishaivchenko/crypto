package com.crypto.funding.api;

import com.crypto.funding.api.dto.TradeOutcomeResponse;
import com.crypto.funding.application.execution.TradeOutcomeQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/outcomes")
public class TradeOutcomeController
{
    public record PnlAggregateResponse(
        BigDecimal totalNetPnlUsd,
        BigDecimal totalGrossPnlUsd,
        BigDecimal totalFeesUsd,
        long closedTrades,
        long profitableTrades
    )
    {
    }

    private final TradeOutcomeQueryService tradeOutcomeQueryService;

    public TradeOutcomeController( TradeOutcomeQueryService tradeOutcomeQueryService )
    {
        this.tradeOutcomeQueryService = tradeOutcomeQueryService;
    }

    @GetMapping("/aggregate")
    public PnlAggregateResponse aggregate()
    {
        TradeOutcomeQueryService.PnlAggregate agg = tradeOutcomeQueryService.aggregate();
        return new PnlAggregateResponse(
            agg.totalNetPnlUsd(),
            agg.totalGrossPnlUsd(),
            agg.totalFeesUsd(),
            agg.closedTrades(),
            agg.profitableTrades()
        );
    }

    @GetMapping
    public Map<Long, TradeOutcomeResponse> byTradeIds( @RequestParam List<Long> armedTradeIds )
    {
        Map<Long, TradeOutcomeQueryService.OutcomeSummary> summaries = tradeOutcomeQueryService.findByArmedTradeIds( armedTradeIds );
        Map<Long, TradeOutcomeResponse> result = new java.util.LinkedHashMap<>();
        summaries.forEach( ( tradeId, summary ) -> result.put( tradeId, new TradeOutcomeResponse(
            summary.grossPnlUsd(),
            summary.netPnlUsd(),
            summary.feesUsd(),
            summary.outcomeCode(),
            summary.evaluatedAt()
        ) ) );
        return result;
    }
}
