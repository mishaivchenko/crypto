package com.crypto.funding.domain.autoapproval;

import com.crypto.funding.domain.ai.AiRecommendation;
import com.crypto.funding.domain.ai.AiSignalAdvice;
import com.crypto.funding.domain.candidate.ReviewDecision;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.domain.liquidity.LiquidityAssessment;
import com.crypto.funding.domain.liquidity.LiquidityScore;
import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AutoApprovalEvaluatorTest
{
    private static final Instant NOW = Instant.parse( "2030-01-01T12:00:00Z" );
    private static final Instant FUNDING_SOON = NOW.plusSeconds( 300 );

    @Test
    void returnsEmptyWhenNoRules()
    {
        Optional<AutoApprovalEvaluator.RuleMatch> result = AutoApprovalEvaluator.evaluate(
            candidate( "gate", "BTC/USDT", new BigDecimal( "0.10" ) ),
            null,
            null,
            List.of()
        );
        assertThat( result ).isEmpty();
    }

    @Test
    void matchesFirstRuleByPriority()
    {
        AutoApprovalRule lowPriority = rule( 200, AutoApprovalAction.AUTO_REJECT, new BigDecimal( "0.05" ), null, List.of(), List.of(), null, List.of() );
        AutoApprovalRule highPriority = rule( 10, AutoApprovalAction.AUTO_EXECUTE, new BigDecimal( "0.05" ), null, List.of(), List.of(), null, List.of() );

        Optional<AutoApprovalEvaluator.RuleMatch> result = AutoApprovalEvaluator.evaluate(
            candidate( "gate", "BTC/USDT", new BigDecimal( "0.10" ) ),
            null,
            null,
            List.of( lowPriority, highPriority )
        );
        assertThat( result ).isPresent();
        assertThat( result.get().action() ).isEqualTo( AutoApprovalAction.AUTO_REJECT );
    }

    @Test
    void rejectsWhenFundingRateBelowMin()
    {
        AutoApprovalRule rule = rule( 1, AutoApprovalAction.AUTO_EXECUTE, new BigDecimal( "0.10" ), null, List.of(), List.of(), null, List.of() );

        Optional<AutoApprovalEvaluator.RuleMatch> result = AutoApprovalEvaluator.evaluate(
            candidate( "gate", "BTC/USDT", new BigDecimal( "0.05" ) ),
            null,
            null,
            List.of( rule )
        );
        assertThat( result ).isEmpty();
    }

    @Test
    void rejectsWhenFundingRateAboveMax()
    {
        AutoApprovalRule rule = rule( 1, AutoApprovalAction.AUTO_EXECUTE, null, new BigDecimal( "0.10" ), List.of(), List.of(), null, List.of() );

        Optional<AutoApprovalEvaluator.RuleMatch> result = AutoApprovalEvaluator.evaluate(
            candidate( "gate", "BTC/USDT", new BigDecimal( "0.50" ) ),
            null,
            null,
            List.of( rule )
        );
        assertThat( result ).isEmpty();
    }

    @Test
    void rejectsWhenVenueNotAllowed()
    {
        AutoApprovalRule rule = rule( 1, AutoApprovalAction.AUTO_EXECUTE, null, null, List.of( "bybit" ), List.of(), null, List.of() );

        Optional<AutoApprovalEvaluator.RuleMatch> result = AutoApprovalEvaluator.evaluate(
            candidate( "gate", "BTC/USDT", new BigDecimal( "0.10" ) ),
            null,
            null,
            List.of( rule )
        );
        assertThat( result ).isEmpty();
    }

    @Test
    void matchesWhenVenueAllowed()
    {
        AutoApprovalRule rule = rule( 1, AutoApprovalAction.AUTO_EXECUTE, null, null, List.of( "gate" ), List.of(), null, List.of() );

        Optional<AutoApprovalEvaluator.RuleMatch> result = AutoApprovalEvaluator.evaluate(
            candidate( "gate", "BTC/USDT", new BigDecimal( "0.10" ) ),
            null,
            null,
            List.of( rule )
        );
        assertThat( result ).isPresent();
        assertThat( result.get().action() ).isEqualTo( AutoApprovalAction.AUTO_EXECUTE );
    }

    @Test
    void rejectsWhenAiRecommendationNotAllowed()
    {
        AutoApprovalRule rule = rule( 1, AutoApprovalAction.AUTO_EXECUTE, null, null, List.of(), List.of( AiRecommendation.GO ), null, List.of() );

        Optional<AutoApprovalEvaluator.RuleMatch> result = AutoApprovalEvaluator.evaluate(
            candidate( "gate", "BTC/USDT", new BigDecimal( "0.10" ) ),
            advice( AiRecommendation.WATCH, 0.7 ),
            null,
            List.of( rule )
        );
        assertThat( result ).isEmpty();
    }

    @Test
    void rejectsWhenAiAdviceAbsentButRecommendationRequired()
    {
        AutoApprovalRule rule = rule( 1, AutoApprovalAction.AUTO_EXECUTE, null, null, List.of(), List.of( AiRecommendation.GO ), null, List.of() );

        Optional<AutoApprovalEvaluator.RuleMatch> result = AutoApprovalEvaluator.evaluate(
            candidate( "gate", "BTC/USDT", new BigDecimal( "0.10" ) ),
            null,
            null,
            List.of( rule )
        );
        assertThat( result ).isEmpty();
    }

    @Test
    void rejectsWhenConfidenceBelowThreshold()
    {
        AutoApprovalRule rule = rule( 1, AutoApprovalAction.AUTO_EXECUTE, null, null, List.of(), List.of(), 0.8, List.of() );

        Optional<AutoApprovalEvaluator.RuleMatch> result = AutoApprovalEvaluator.evaluate(
            candidate( "gate", "BTC/USDT", new BigDecimal( "0.10" ) ),
            advice( AiRecommendation.GO, 0.7 ),
            null,
            List.of( rule )
        );
        assertThat( result ).isEmpty();
    }

    @Test
    void rejectsWhenLiquidityScoreNotAllowed()
    {
        AutoApprovalRule rule = rule( 1, AutoApprovalAction.AUTO_EXECUTE, null, null, List.of(), List.of(), null, List.of( LiquidityScore.GOOD, LiquidityScore.EXCELLENT ) );

        Optional<AutoApprovalEvaluator.RuleMatch> result = AutoApprovalEvaluator.evaluate(
            candidate( "gate", "BTC/USDT", new BigDecimal( "0.10" ) ),
            null,
            liquidity( LiquidityScore.THIN ),
            List.of( rule )
        );
        assertThat( result ).isEmpty();
    }

    @Test
    void matchesAllConditionsSatisfied()
    {
        AutoApprovalRule rule = rule(
            1,
            AutoApprovalAction.AUTO_EXECUTE,
            new BigDecimal( "0.05" ),
            new BigDecimal( "1.00" ),
            List.of( "gate" ),
            List.of( AiRecommendation.GO ),
            0.7,
            List.of( LiquidityScore.GOOD, LiquidityScore.EXCELLENT )
        );

        Optional<AutoApprovalEvaluator.RuleMatch> result = AutoApprovalEvaluator.evaluate(
            candidate( "gate", "BTC/USDT", new BigDecimal( "0.10" ) ),
            advice( AiRecommendation.GO, 0.85 ),
            liquidity( LiquidityScore.GOOD ),
            List.of( rule )
        );
        assertThat( result ).isPresent();
        assertThat( result.get().rule().name() ).isEqualTo( "test-rule" );
        assertThat( result.get().action() ).isEqualTo( AutoApprovalAction.AUTO_EXECUTE );
    }

    @Test
    void noConditionsRuleMatchesEverything()
    {
        AutoApprovalRule rule = rule( 1, AutoApprovalAction.AUTO_EXECUTE, null, null, List.of(), List.of(), null, List.of() );

        Optional<AutoApprovalEvaluator.RuleMatch> result = AutoApprovalEvaluator.evaluate(
            candidate( null, "BTC/USDT", null ),
            null,
            null,
            List.of( rule )
        );
        assertThat( result ).isPresent();
    }

    @Test
    void resolvesVenueFromHintsWhenSourceVenueAbsent()
    {
        AutoApprovalRule rule = rule( 1, AutoApprovalAction.AUTO_EXECUTE, null, null, List.of( "gate" ), List.of(), null, List.of() );

        SignalCandidate candidateWithHint = new SignalCandidate(
            1L, "FUNDING_API", null, null, null,
            null, "BTC/USDT", "BTC/USDT",
            List.of( "gate" ),
            NOW, SignalCandidateStatus.NORMALIZED,
            null, null, null, null,
            FUNDING_SOON, new BigDecimal( "0.10" ), null, NOW, NOW
        );

        Optional<AutoApprovalEvaluator.RuleMatch> result = AutoApprovalEvaluator.evaluate(
            candidateWithHint, null, null, List.of( rule )
        );
        assertThat( result ).isPresent();
    }

    // --- helpers ---

    private static SignalCandidate candidate( String venue, String symbol, BigDecimal rate )
    {
        return new SignalCandidate(
            1L, "FUNDING_API", null, null, null,
            venue, symbol, symbol,
            venue != null ? List.of( venue ) : List.of(),
            NOW, SignalCandidateStatus.NORMALIZED,
            null, null, null, null,
            FUNDING_SOON, rate, null, NOW, NOW
        );
    }

    private static AiSignalAdvice advice( AiRecommendation recommendation, double confidence )
    {
        return new AiSignalAdvice( 1L, 1L, recommendation, confidence, null, null, null, null, NOW, NOW );
    }

    private static LiquidityAssessment liquidity( LiquidityScore score )
    {
        return new LiquidityAssessment(
            "assessment-1", null, 1L, "gate", "BTC/USDT", TradeSide.SHORT,
            null, null, null, null, null, null, null, null, null,
            score, NOW, NOW.plusSeconds( 60 )
        );
    }

    private static AutoApprovalRule rule(
        int priority,
        AutoApprovalAction action,
        BigDecimal minRate,
        BigDecimal maxRate,
        List<String> venues,
        List<AiRecommendation> recommendations,
        Double minConfidence,
        List<LiquidityScore> liquidityScores
    )
    {
        return new AutoApprovalRule(
            (long) priority, "test-rule", true, "BOTH",
            minRate, maxRate,
            venues, recommendations, minConfidence, liquidityScores,
            new BigDecimal( "100" ), TradeSide.SHORT,
            action, priority, null, NOW, NOW
        );
    }
}
