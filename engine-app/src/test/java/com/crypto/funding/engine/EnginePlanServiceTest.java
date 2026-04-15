package com.crypto.funding.engine;

import com.crypto.funding.application.query.TradeQueryService;
import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeArmSource;
import com.crypto.funding.domain.trade.TradeSide;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnginePlanServiceTest
{
    private final TradeQueryService tradeQueryService = mock( TradeQueryService.class );
    private final FundingEventJpaRepository fundingEventRepository = mock( FundingEventJpaRepository.class );
    private final EngineProperties engineProperties = new EngineProperties();

    private EnginePlanService service;
    private Instant now;

    @BeforeEach
    void setUp()
    {
        now = Instant.parse( "2030-01-01T00:00:00Z" );
        engineProperties.setLookaheadMinutes( 120 );
        engineProperties.setOverdueGraceSeconds( 30 );
        service = new EnginePlanService(
            tradeQueryService,
            fundingEventRepository,
            engineProperties,
            Clock.fixed( now, ZoneOffset.UTC )
        );
    }

    @Test
    void marksTradeAsWaitingEntryBeforePlannedEntry()
    {
        ArmedTrade trade = armedTrade( 10L, ArmedTradeState.ARMED, now.plusSeconds( 300 ), now.plusSeconds( 900 ) );
        mockTrade( trade, fundingEvent( 10L, "binance", "BTC/USDT", now.plusSeconds( 600 ) ) );

        EngineExecutionPlan plan = service.listPlans().getFirst();

        assertThat( plan.status() ).isEqualTo( EnginePlanStatus.WAITING_ENTRY );
        assertThat( plan.nextActionAt() ).isEqualTo( now.plusSeconds( 300 ).minusMillis( 35 ) );
        assertThat( plan.entryAttempts() ).hasSize( 3 );
        assertThat( plan.entryAttempts().getFirst().triggerAt() ).isEqualTo( now.plusSeconds( 300 ).minusMillis( 35 ) );
        assertThat( plan.entryAttempts().get( 1 ).targetEntryAt() ).isEqualTo( now.plusSeconds( 300 ).plusMillis( 150 ) );
        assertThat( plan.summary() ).contains( "Ожидаем вход" );
    }

    @Test
    void marksTradeAsEntryWindowWhenEntryTimeIsDue()
    {
        ArmedTrade trade = armedTrade( 11L, ArmedTradeState.ARMED, now.minusSeconds( 5 ), now.plusSeconds( 600 ) );
        mockTrade( trade, fundingEvent( 11L, "bybit", "ETH/USDT", now.plusSeconds( 120 ) ) );

        EngineExecutionPlan plan = service.listPlans().getFirst();

        assertThat( plan.status() ).isEqualTo( EnginePlanStatus.ENTRY_WINDOW );
        assertThat( plan.summary() ).contains( "Окно входа активно" );
    }

    @Test
    void marksOpenTradeAsWaitingExit()
    {
        ArmedTrade trade = armedTrade( 12L, ArmedTradeState.OPEN, now.minusSeconds( 60 ), now.plusSeconds( 120 ) );
        mockTrade( trade, fundingEvent( 12L, "gate", "SOL/USDT", now.minusSeconds( 10 ) ) );

        EngineExecutionPlan plan = service.listPlans().getFirst();

        assertThat( plan.status() ).isEqualTo( EnginePlanStatus.WAITING_EXIT );
        assertThat( plan.nextActionAt() ).isEqualTo( now.plusSeconds( 120 ) );
    }

    @Test
    void marksMissedEntryAsOverdue()
    {
        ArmedTrade trade = armedTrade( 13L, ArmedTradeState.ARMED, now.minusSeconds( 90 ), now.plusSeconds( 600 ) );
        mockTrade( trade, fundingEvent( 13L, "binance", "XRP/USDT", now.plusSeconds( 50 ) ) );

        EngineExecutionPlan plan = service.listPlans().getFirst();

        assertThat( plan.status() ).isEqualTo( EnginePlanStatus.OVERDUE );
        assertThat( plan.summary() ).contains( "нужен разбор оператора" );
    }

    @Test
    void filtersOutTradesOutsideLookaheadWindow()
    {
        ArmedTrade farTrade = armedTrade( 14L, ArmedTradeState.ARMED, now.plusSeconds( 60L * 60L * 5L ), now.plusSeconds( 60L * 60L * 6L ) );
        mockTrade( farTrade, fundingEvent( 14L, "bybit", "ADA/USDT", now.plusSeconds( 60L * 60L * 5L ) ) );

        assertThat( service.listPlans() ).isEmpty();
    }

    @Test
    void summaryCountsActionablePlans()
    {
        ArmedTrade dueTrade = armedTrade( 15L, ArmedTradeState.ARMED, now.minusSeconds( 10 ), now.plusSeconds( 200 ) );
        FundingEvent dueEvent = fundingEvent( 15L, "binance", "BTC/USDT", now.plusSeconds( 30 ) );

        ArmedTrade waitingTrade = armedTrade( 16L, ArmedTradeState.ARMED, now.plusSeconds( 300 ), now.plusSeconds( 900 ) );
        FundingEvent waitingEvent = fundingEvent( 16L, "gate", "TON/USDT", now.plusSeconds( 600 ) );

        when( tradeQueryService.listArmedTrades() ).thenReturn( List.of( dueTrade, waitingTrade ) );
        when( fundingEventRepository.findById( 15L ) ).thenReturn( Optional.of( toEntity( dueEvent ) ) );
        when( fundingEventRepository.findById( 16L ) ).thenReturn( Optional.of( toEntity( waitingEvent ) ) );

        EngineSummaryResponse summary = service.summary();

        assertThat( summary.totalPlans() ).isEqualTo( 2 );
        assertThat( summary.actionablePlans() ).isEqualTo( 1 );
        assertThat( summary.statusBreakdown().get( EnginePlanStatus.ENTRY_WINDOW ) ).isEqualTo( 1L );
        assertThat( summary.statusBreakdown().get( EnginePlanStatus.WAITING_ENTRY ) ).isEqualTo( 1L );
    }

    private void mockTrade( ArmedTrade trade, FundingEvent fundingEvent )
    {
        when( tradeQueryService.listArmedTrades() ).thenReturn( List.of( trade ) );
        when( fundingEventRepository.findById( fundingEvent.id() ) ).thenReturn( Optional.of( toEntity( fundingEvent ) ) );
    }

    private static ArmedTrade armedTrade( Long fundingEventId, ArmedTradeState state, Instant plannedEntryAt, Instant plannedExitAt )
    {
        return new ArmedTrade(
            fundingEventId + 1000,
            fundingEventId,
            new BigDecimal( "25.00" ),
            TradeSide.SHORT,
            plannedEntryAt,
            plannedExitAt,
            Instant.parse( "2029-12-31T23:00:00Z" ),
            1_000L,
            null,
            null,
            3,
            150L,
            25L,
            10L,
            35L,
            TradeArmSource.EVENT_API,
            state,
            "test",
            Instant.parse( "2029-12-31T23:00:00Z" ),
            Instant.parse( "2029-12-31T23:00:00Z" )
        );
    }

    private static FundingEvent fundingEvent( Long id, String venue, String symbol, Instant fundingTime )
    {
        return new FundingEvent(
            id,
            venue,
            symbol,
            fundingTime,
            new BigDecimal( "0.01" ),
            FundingEventStatus.DISCOVERED,
            "FUNDING_API",
            "test",
            null,
            Instant.parse( "2029-12-31T22:00:00Z" ),
            Instant.parse( "2029-12-31T22:00:00Z" ),
            Instant.parse( "2029-12-31T22:00:00Z" )
        );
    }

    private static FundingEventEntity toEntity( FundingEvent fundingEvent )
    {
        FundingEventEntity entity = new FundingEventEntity();
        ReflectionTestUtils.setField( entity, "id", fundingEvent.id() );
        entity.setVenue( fundingEvent.venue() );
        entity.setSymbol( fundingEvent.symbol() );
        entity.setFundingTime( fundingEvent.fundingTime() );
        entity.setFundingRatePct( fundingEvent.fundingRatePct() );
        entity.setStatus( fundingEvent.status() );
        entity.setSourceType( fundingEvent.sourceType() );
        entity.setSourceRef( fundingEvent.sourceRef() );
        entity.setSignalCandidateId( fundingEvent.signalCandidateId() );
        entity.setDiscoveredAt( fundingEvent.discoveredAt() );
        return entity;
    }
}
