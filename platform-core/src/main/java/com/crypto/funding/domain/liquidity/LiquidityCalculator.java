package com.crypto.funding.domain.liquidity;

import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LiquidityCalculator
{
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal BPS_DIVISOR = new BigDecimal( "10000" );

    private LiquidityCalculator()
    {
    }

    public static LiquidityAssessment assess(
        OrderBookSnapshot snapshot,
        TradeSide side,
        BigDecimal maxSlippageBps,
        BigDecimal safetyHaircut,
        LiquidityThresholds thresholds,
        Instant expiresAt
    )
    {
        if( snapshot == null )
        {
            throw new IllegalArgumentException( "snapshot must not be null" );
        }
        if( side != TradeSide.SHORT )
        {
            throw new IllegalArgumentException( "only SHORT side is supported in this version" );
        }
        if( maxSlippageBps == null || maxSlippageBps.signum() < 0 )
        {
            throw new IllegalArgumentException( "maxSlippageBps must be non-negative" );
        }
        if( safetyHaircut == null || safetyHaircut.signum() < 0 || safetyHaircut.compareTo( BigDecimal.ONE ) > 0 )
        {
            throw new IllegalArgumentException( "safetyHaircut must be between 0 and 1" );
        }

        BigDecimal bestBid = bestBid( snapshot.bids() );
        BigDecimal bestAsk = bestAsk( snapshot.asks() );

        BigDecimal spreadBps = computeSpreadBps( bestBid, bestAsk );
        BigDecimal entryDepth = computeShortEntryDepth( snapshot.bids(), bestBid, maxSlippageBps );
        BigDecimal exitDepth = computeShortExitDepth( snapshot.asks(), bestAsk, maxSlippageBps );
        BigDecimal roundTripSafe = entryDepth.min( exitDepth );
        BigDecimal recommended = roundTripSafe.multiply( safetyHaircut, MC ).setScale( 8, RoundingMode.HALF_UP );
        LiquidityScore score = scoreFor( recommended, spreadBps, maxSlippageBps, thresholds );

        return new LiquidityAssessment(
            UUID.randomUUID().toString(),
            null,
            snapshot.venue(),
            snapshot.symbol(),
            side,
            bestBid,
            bestAsk,
            spreadBps,
            maxSlippageBps,
            entryDepth,
            exitDepth,
            roundTripSafe,
            safetyHaircut,
            recommended,
            score,
            snapshot.sampledAt(),
            expiresAt
        );
    }

    // SHORT entry: sell into bids — sum bids from bestBid down to minAcceptableEntryPrice
    static BigDecimal computeShortEntryDepth( List<OrderBookLevel> bids, BigDecimal bestBid, BigDecimal maxSlippageBps )
    {
        if( bestBid == null || bestBid.signum() <= 0 || bids.isEmpty() )
        {
            return BigDecimal.ZERO;
        }
        BigDecimal slippageFraction = maxSlippageBps.divide( BPS_DIVISOR, MC );
        BigDecimal minAcceptable = bestBid.multiply( BigDecimal.ONE.subtract( slippageFraction, MC ), MC );

        BigDecimal total = BigDecimal.ZERO;
        for( OrderBookLevel level : bids )
        {
            if( level.price().compareTo( minAcceptable ) >= 0 )
            {
                total = total.add( level.notional() );
            }
        }
        return total.setScale( 8, RoundingMode.HALF_UP );
    }

    // SHORT exit: buy back from asks — sum asks from bestAsk up to maxAcceptableExitPrice
    static BigDecimal computeShortExitDepth( List<OrderBookLevel> asks, BigDecimal bestAsk, BigDecimal maxSlippageBps )
    {
        if( bestAsk == null || bestAsk.signum() <= 0 || asks.isEmpty() )
        {
            return BigDecimal.ZERO;
        }
        BigDecimal slippageFraction = maxSlippageBps.divide( BPS_DIVISOR, MC );
        BigDecimal maxAcceptable = bestAsk.multiply( BigDecimal.ONE.add( slippageFraction, MC ), MC );

        BigDecimal total = BigDecimal.ZERO;
        for( OrderBookLevel level : asks )
        {
            if( level.price().compareTo( maxAcceptable ) <= 0 )
            {
                total = total.add( level.notional() );
            }
        }
        return total.setScale( 8, RoundingMode.HALF_UP );
    }

    static BigDecimal computeSpreadBps( BigDecimal bestBid, BigDecimal bestAsk )
    {
        if( bestBid == null || bestAsk == null || bestBid.signum() <= 0 || bestAsk.signum() <= 0 )
        {
            return null;
        }
        BigDecimal mid = bestBid.add( bestAsk ).divide( new BigDecimal( "2" ), MC );
        return bestAsk.subtract( bestBid )
                      .divide( mid, MC )
                      .multiply( BPS_DIVISOR )
                      .setScale( 4, RoundingMode.HALF_UP );
    }

    private static BigDecimal bestBid( List<OrderBookLevel> bids )
    {
        return bids.stream()
                   .map( OrderBookLevel::price )
                   .max( BigDecimal::compareTo )
                   .orElse( null );
    }

    private static BigDecimal bestAsk( List<OrderBookLevel> asks )
    {
        return asks.stream()
                   .map( OrderBookLevel::price )
                   .min( BigDecimal::compareTo )
                   .orElse( null );
    }

    static LiquidityScore scoreFor(
        BigDecimal recommendedNotional,
        BigDecimal spreadBps,
        BigDecimal maxSlippageBps,
        LiquidityThresholds t
    )
    {
        if( recommendedNotional == null || recommendedNotional.compareTo( t.minTradableNotional() ) < 0 )
        {
            return LiquidityScore.UNTRADABLE;
        }
        if( spreadBps != null && maxSlippageBps != null && spreadBps.compareTo( maxSlippageBps ) > 0 )
        {
            return LiquidityScore.UNTRADABLE;
        }
        if( recommendedNotional.compareTo( t.excellentNotional() ) >= 0 )
        {
            return LiquidityScore.EXCELLENT;
        }
        if( recommendedNotional.compareTo( t.goodNotional() ) >= 0 )
        {
            return LiquidityScore.GOOD;
        }
        if( recommendedNotional.compareTo( t.mediumNotional() ) >= 0 )
        {
            return LiquidityScore.MEDIUM;
        }
        return LiquidityScore.THIN;
    }
}
