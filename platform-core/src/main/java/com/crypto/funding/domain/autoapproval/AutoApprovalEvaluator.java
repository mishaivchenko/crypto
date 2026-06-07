package com.crypto.funding.domain.autoapproval;

import com.crypto.funding.domain.ai.AiSignalAdvice;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.liquidity.LiquidityAssessment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Pure domain evaluator — no Spring, no I/O.
 * Finds the first matching active rule (lowest priority number wins).
 */
public final class AutoApprovalEvaluator
{
    private AutoApprovalEvaluator() {}

    public record RuleMatch( AutoApprovalRule rule, AutoApprovalAction action ) {}

    public static Optional<RuleMatch> evaluate(
        SignalCandidate candidate,
        AiSignalAdvice advice,
        LiquidityAssessment liquidity,
        List<AutoApprovalRule> activeRules
    )
    {
        if( activeRules == null || activeRules.isEmpty() )
        {
            return Optional.empty();
        }

        for( AutoApprovalRule rule : activeRules )
        {
            if( matches( rule, candidate, advice, liquidity ) )
            {
                return Optional.of( new RuleMatch( rule, rule.action() ) );
            }
        }
        return Optional.empty();
    }

    private static boolean matches(
        AutoApprovalRule rule,
        SignalCandidate candidate,
        AiSignalAdvice advice,
        LiquidityAssessment liquidity
    )
    {
        // funding rate check
        BigDecimal rate = candidate.sourceFundingRatePct();
        if( rule.minFundingRatePct() != null )
        {
            if( rate == null || rate.compareTo( rule.minFundingRatePct() ) < 0 )
            {
                return false;
            }
        }
        if( rule.maxFundingRatePct() != null )
        {
            if( rate == null || rate.compareTo( rule.maxFundingRatePct() ) > 0 )
            {
                return false;
            }
        }

        // venue check
        if( !rule.allowedVenues().isEmpty() )
        {
            String venue = resolveVenue( candidate );
            if( venue == null || !rule.allowedVenues().contains( venue ) )
            {
                return false;
            }
        }

        // AI recommendation check
        if( !rule.allowedAiRecommendations().isEmpty() )
        {
            if( advice == null || !rule.allowedAiRecommendations().contains( advice.recommendation() ) )
            {
                return false;
            }
        }

        // AI confidence check
        if( rule.minAiConfidence() != null )
        {
            if( advice == null || advice.confidence() < rule.minAiConfidence() )
            {
                return false;
            }
        }

        // liquidity score check
        if( !rule.allowedLiquidityScores().isEmpty() )
        {
            if( liquidity == null || !rule.allowedLiquidityScores().contains( liquidity.score() ) )
            {
                return false;
            }
        }

        return true;
    }

    private static String resolveVenue( SignalCandidate candidate )
    {
        if( candidate.sourceVenue() != null && !candidate.sourceVenue().isBlank() )
        {
            return candidate.sourceVenue();
        }
        List<String> hints = candidate.venueHints();
        if( hints != null && hints.size() == 1 )
        {
            return hints.get( 0 );
        }
        return null;
    }
}
