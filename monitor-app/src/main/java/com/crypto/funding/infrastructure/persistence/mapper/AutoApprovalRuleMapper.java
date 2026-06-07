package com.crypto.funding.infrastructure.persistence.mapper;

import com.crypto.funding.domain.ai.AiRecommendation;
import com.crypto.funding.domain.autoapproval.AutoApprovalRule;
import com.crypto.funding.domain.liquidity.LiquidityScore;
import com.crypto.funding.infrastructure.persistence.model.AutoApprovalRuleEntity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class AutoApprovalRuleMapper
{
    private AutoApprovalRuleMapper() {}

    public static AutoApprovalRule toDomain( AutoApprovalRuleEntity entity )
    {
        return new AutoApprovalRule(
            entity.getId(),
            entity.getName(),
            entity.isEnabled(),
            entity.getMode(),
            entity.getMinFundingRatePct(),
            entity.getMaxFundingRatePct(),
            parseList( entity.getAllowedVenues() ),
            parseEnumList( entity.getAllowedAiRecommendations(), AiRecommendation.class ),
            entity.getMinAiConfidence() != null ? entity.getMinAiConfidence().doubleValue() : null,
            parseEnumList( entity.getAllowedLiquidityScores(), LiquidityScore.class ),
            entity.getDefaultNotionalUsd(),
            entity.getDefaultSide(),
            entity.getAction(),
            entity.getPriority(),
            entity.getNotes(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public static String serializeList( List<String> values )
    {
        if( values == null || values.isEmpty() )
        {
            return null;
        }
        return String.join( ",", values );
    }

    private static List<String> parseList( String raw )
    {
        if( raw == null || raw.isBlank() )
        {
            return List.of();
        }
        return Arrays.stream( raw.split( "," ) )
                     .map( String::trim )
                     .filter( s -> !s.isEmpty() )
                     .toList();
    }

    private static <E extends Enum<E>> List<E> parseEnumList( String raw, Class<E> enumClass )
    {
        if( raw == null || raw.isBlank() )
        {
            return List.of();
        }
        return Arrays.stream( raw.split( "," ) )
                     .map( String::trim )
                     .filter( s -> !s.isEmpty() )
                     .map( s -> Enum.valueOf( enumClass, s ) )
                     .collect( Collectors.toList() );
    }
}
