package com.crypto.funding.api;

import com.crypto.funding.api.dto.LiquidityAssessmentResponse;
import com.crypto.funding.application.liquidity.LiquidityAssessmentService;
import com.crypto.funding.domain.liquidity.LiquidityAssessment;
import com.crypto.funding.domain.liquidity.LiquidityScore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LiquidityAssessmentController
{
    private final LiquidityAssessmentService liquidityAssessmentService;

    public LiquidityAssessmentController( LiquidityAssessmentService liquidityAssessmentService )
    {
        this.liquidityAssessmentService = liquidityAssessmentService;
    }

    @PostMapping("/venues/{venue}/symbols/{venueSymbol}/liquidity-assessment")
    public LiquidityAssessmentResponse assess(
        @PathVariable String venue,
        @PathVariable String venueSymbol,
        @RequestParam(required = false) Long tradeId
    )
    {
        LiquidityAssessment assessment = liquidityAssessmentService.assess( venue, venueSymbol, tradeId );
        return toResponse( assessment );
    }

    @PostMapping("/candidates/{candidateId}/liquidity")
    public LiquidityAssessmentResponse assessForCandidate(
        @PathVariable Long candidateId,
        @RequestParam String venue,
        @RequestParam String venueSymbol
    )
    {
        LiquidityAssessment assessment = liquidityAssessmentService.assessForCandidate( venue, venueSymbol, candidateId );
        return toResponse( assessment );
    }

    @GetMapping("/candidates/{candidateId}/liquidity")
    public ResponseEntity<LiquidityAssessmentResponse> getForCandidate( @PathVariable Long candidateId )
    {
        return liquidityAssessmentService.findLatestForCandidate( candidateId )
                                         .map( a -> ResponseEntity.ok( toResponse( a ) ) )
                                         .orElse( ResponseEntity.notFound().build() );
    }

    @GetMapping("/trades/{tradeId}/liquidity")
    public ResponseEntity<LiquidityAssessmentResponse> getForTrade( @PathVariable Long tradeId )
    {
        return liquidityAssessmentService.findLatestForTrade( tradeId )
                                         .map( a -> ResponseEntity.ok( toResponse( a ) ) )
                                         .orElse( ResponseEntity.notFound().build() );
    }

    @PostMapping("/trades/{tradeId}/refresh-liquidity")
    public ResponseEntity<LiquidityAssessmentResponse> refreshForTrade(
        @PathVariable Long tradeId,
        @RequestParam String venue,
        @RequestParam String venueSymbol
    )
    {
        LiquidityAssessment assessment = liquidityAssessmentService.assess( venue, venueSymbol, tradeId );
        return ResponseEntity.ok( toResponse( assessment ) );
    }

    private static LiquidityAssessmentResponse toResponse( LiquidityAssessment a )
    {
        boolean warning = a.score() == LiquidityScore.THIN || a.score() == LiquidityScore.UNTRADABLE;
        return new LiquidityAssessmentResponse(
            a.id(),
            a.tradeId(),
            a.venue(),
            a.symbol(),
            a.side(),
            a.bestBid(),
            a.bestAsk(),
            a.spreadBps(),
            a.maxSlippageBps(),
            a.entryBidDepthNotional(),
            a.exitAskDepthNotional(),
            a.roundTripSafeNotional(),
            a.safetyHaircut(),
            a.recommendedMaxOrderNotional(),
            a.score(),
            warning,
            a.sampledAt(),
            a.expiresAt()
        );
    }
}
