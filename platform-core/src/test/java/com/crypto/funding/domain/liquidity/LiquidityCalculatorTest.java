package com.crypto.funding.domain.liquidity;

import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LiquidityCalculatorTest
{
    private static final BigDecimal SLIPPAGE_50_BPS = new BigDecimal( "50" );
    private static final BigDecimal HAIRCUT_30 = new BigDecimal( "0.30" );
    private static final LiquidityThresholds THRESHOLDS = LiquidityThresholds.DEFAULT;

    // ── 1. SHORT entry depth uses BID side ──────────────────────────────────

    @Test
    void shortEntryDepth_usesBidSide_notAskSide()
    {
        BigDecimal bestBid = new BigDecimal( "100" );
        // Only bids within 50 bps of 100 = >= 99.50
        List<OrderBookLevel> bids = List.of(
            new OrderBookLevel( new BigDecimal( "100" ), new BigDecimal( "10" ) ),  // 1000 notional — included
            new OrderBookLevel( new BigDecimal( "99.6" ), new BigDecimal( "5" ) ),   // 498 — included
            new OrderBookLevel( new BigDecimal( "99.4" ), new BigDecimal( "5" ) )    // 497 — excluded (below minAcceptable)
        );
        BigDecimal depth = LiquidityCalculator.computeShortEntryDepth( bids, bestBid, SLIPPAGE_50_BPS );
        // included: 100*10 + 99.6*5 = 1000 + 498 = 1498
        assertThat( depth ).isEqualByComparingTo( "1498.00000000" );
    }

    // ── 2. SHORT exit depth uses ASK side ────────────────────────────────────

    @Test
    void shortExitDepth_usesAskSide_notBidSide()
    {
        BigDecimal bestAsk = new BigDecimal( "100" );
        // maxAcceptable = 100 * (1 + 50/10000) = 100.50
        List<OrderBookLevel> asks = List.of(
            new OrderBookLevel( new BigDecimal( "100" ), new BigDecimal( "8" ) ),    // 800 — included
            new OrderBookLevel( new BigDecimal( "100.4" ), new BigDecimal( "4" ) ),  // 401.6 — included
            new OrderBookLevel( new BigDecimal( "100.6" ), new BigDecimal( "3" ) )   // excluded (above maxAcceptable)
        );
        BigDecimal depth = LiquidityCalculator.computeShortExitDepth( asks, bestAsk, SLIPPAGE_50_BPS );
        // included: 100*8 + 100.4*4 = 800 + 401.6 = 1201.6
        assertThat( depth ).isEqualByComparingTo( "1201.60000000" );
    }

    // ── 3. Levels outside max slippage are excluded ──────────────────────────

    @Test
    void levelsOutsideMaxSlippage_areExcluded()
    {
        BigDecimal bestBid = new BigDecimal( "1000" );
        // 50 bps → minAcceptable = 1000 * (1 - 0.005) = 995
        List<OrderBookLevel> bids = List.of(
            new OrderBookLevel( new BigDecimal( "995" ), new BigDecimal( "1" ) ),  // exactly at boundary — included
            new OrderBookLevel( new BigDecimal( "994.9" ), new BigDecimal( "1" ) ) // below — excluded
        );
        BigDecimal depth = LiquidityCalculator.computeShortEntryDepth( bids, bestBid, SLIPPAGE_50_BPS );
        assertThat( depth ).isEqualByComparingTo( "995.00000000" );
    }

    // ── 4. roundTripSafeNotional = min(entryDepth, exitDepth) ────────────────

    @Test
    void roundTripSafeNotional_isMinOfEntryAndExit()
    {
        // entry depth will be small, exit depth large
        List<OrderBookLevel> bids = List.of(
            new OrderBookLevel( new BigDecimal( "100" ), new BigDecimal( "10" ) )  // 1000
        );
        List<OrderBookLevel> asks = List.of(
            new OrderBookLevel( new BigDecimal( "100.1" ), new BigDecimal( "100" ) ) // 10010
        );
        OrderBookSnapshot snap = new OrderBookSnapshot( "gate", "BTC_USDT", bids, asks, Instant.now() );
        LiquidityAssessment result = LiquidityCalculator.assess( snap, TradeSide.SHORT, SLIPPAGE_50_BPS, HAIRCUT_30, THRESHOLDS, null );

        assertThat( result.entryBidDepthNotional() ).isEqualByComparingTo( "1000.00000000" );
        assertThat( result.roundTripSafeNotional() ).isEqualByComparingTo( result.entryBidDepthNotional() );
    }

    // ── 5. Safety haircut is applied correctly ────────────────────────────────

    @Test
    void safetyHaircut_isApplied()
    {
        List<OrderBookLevel> bids = List.of(
            new OrderBookLevel( new BigDecimal( "100" ), new BigDecimal( "100" ) )  // 10000 entry depth
        );
        List<OrderBookLevel> asks = List.of(
            new OrderBookLevel( new BigDecimal( "100.1" ), new BigDecimal( "100" ) )  // 10010 exit depth
        );
        OrderBookSnapshot snap = new OrderBookSnapshot( "bybit", "BTCUSDT", bids, asks, Instant.now() );
        LiquidityAssessment result = LiquidityCalculator.assess( snap, TradeSide.SHORT, SLIPPAGE_50_BPS, HAIRCUT_30, THRESHOLDS, null );

        BigDecimal expected = result.roundTripSafeNotional().multiply( HAIRCUT_30 );
        assertThat( result.recommendedMaxOrderNotional() ).isEqualByComparingTo( expected.setScale( 8, java.math.RoundingMode.HALF_UP ) );
    }

    // ── 6. Liquidity score is assigned correctly ──────────────────────────────

    @Test
    void score_excellent_whenRecommendedAboveExcellentThreshold()
    {
        BigDecimal recommended = new BigDecimal( "150000" );
        LiquidityScore score = LiquidityCalculator.scoreFor( recommended, new BigDecimal( "2" ), SLIPPAGE_50_BPS, THRESHOLDS );
        assertThat( score ).isEqualTo( LiquidityScore.EXCELLENT );
    }

    @Test
    void score_good_whenRecommendedAboveGoodThreshold()
    {
        BigDecimal recommended = new BigDecimal( "30000" );
        LiquidityScore score = LiquidityCalculator.scoreFor( recommended, new BigDecimal( "2" ), SLIPPAGE_50_BPS, THRESHOLDS );
        assertThat( score ).isEqualTo( LiquidityScore.GOOD );
    }

    @Test
    void score_medium_whenRecommendedAboveMediumThreshold()
    {
        BigDecimal recommended = new BigDecimal( "8000" );
        LiquidityScore score = LiquidityCalculator.scoreFor( recommended, new BigDecimal( "2" ), SLIPPAGE_50_BPS, THRESHOLDS );
        assertThat( score ).isEqualTo( LiquidityScore.MEDIUM );
    }

    @Test
    void score_thin_whenRecommendedAboveMinButBelowMedium()
    {
        BigDecimal recommended = new BigDecimal( "300" );
        LiquidityScore score = LiquidityCalculator.scoreFor( recommended, new BigDecimal( "2" ), SLIPPAGE_50_BPS, THRESHOLDS );
        assertThat( score ).isEqualTo( LiquidityScore.THIN );
    }

    @Test
    void score_untradable_whenRecommendedBelowMinTradable()
    {
        BigDecimal recommended = new BigDecimal( "10" );
        LiquidityScore score = LiquidityCalculator.scoreFor( recommended, new BigDecimal( "2" ), SLIPPAGE_50_BPS, THRESHOLDS );
        assertThat( score ).isEqualTo( LiquidityScore.UNTRADABLE );
    }

    @Test
    void score_untradable_whenSpreadExceedsMaxSlippage()
    {
        BigDecimal recommended = new BigDecimal( "50000" );
        BigDecimal spreadBps = new BigDecimal( "100" ); // wider than 50 bps slippage
        LiquidityScore score = LiquidityCalculator.scoreFor( recommended, spreadBps, SLIPPAGE_50_BPS, THRESHOLDS );
        assertThat( score ).isEqualTo( LiquidityScore.UNTRADABLE );
    }

    // ── 7. assess() rejects non-SHORT side ───────────────────────────────────

    @Test
    void assess_throwsForLongSide()
    {
        OrderBookSnapshot snap = new OrderBookSnapshot( "gate", "BTC_USDT", List.of(), List.of(), Instant.now() );
        assertThatThrownBy( () -> LiquidityCalculator.assess( snap, TradeSide.LONG, SLIPPAGE_50_BPS, HAIRCUT_30, THRESHOLDS, null ) )
            .isInstanceOf( IllegalArgumentException.class )
            .hasMessageContaining( "SHORT" );
    }

    // ── 8. Spread calculation ─────────────────────────────────────────────────

    @Test
    void spreadBps_computedCorrectly()
    {
        // bid 100, ask 100.1 → spread = 0.1 / 100.05 * 10000 ≈ 9.995 bps
        BigDecimal spread = LiquidityCalculator.computeSpreadBps( new BigDecimal( "100" ), new BigDecimal( "100.1" ) );
        assertThat( spread ).isNotNull();
        assertThat( spread.doubleValue() ).isCloseTo( 9.995, within( 0.01 ) );
    }

    @Test
    void spreadBps_nullWhenNoBidOrAsk()
    {
        assertThat( LiquidityCalculator.computeSpreadBps( null, new BigDecimal( "100" ) ) ).isNull();
        assertThat( LiquidityCalculator.computeSpreadBps( new BigDecimal( "100" ), null ) ).isNull();
    }

    // ── 9. Empty order book returns zero depth ────────────────────────────────

    @Test
    void emptyBids_returnsZeroEntryDepth()
    {
        BigDecimal depth = LiquidityCalculator.computeShortEntryDepth( List.of(), null, SLIPPAGE_50_BPS );
        assertThat( depth ).isEqualByComparingTo( BigDecimal.ZERO );
    }

    @Test
    void emptyAsks_returnsZeroExitDepth()
    {
        BigDecimal depth = LiquidityCalculator.computeShortExitDepth( List.of(), null, SLIPPAGE_50_BPS );
        assertThat( depth ).isEqualByComparingTo( BigDecimal.ZERO );
    }
}
