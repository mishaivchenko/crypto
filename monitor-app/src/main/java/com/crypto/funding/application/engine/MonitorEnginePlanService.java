package com.crypto.funding.application.engine;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.engine.planning.EngineEntryAttemptScheduleBuilder;
import com.crypto.funding.application.engine.planning.EnginePlanLookaheadFilter;
import com.crypto.funding.application.engine.planning.EnginePlanStatusCalculator;
import com.crypto.funding.application.engine.planning.EnginePlanSummaryFormatter;
import com.crypto.funding.application.query.TradeQueryService;
import com.crypto.funding.application.venue.VenueLatencyProbeService;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.config.MonitorEnginePlanProperties;
import com.crypto.funding.config.MonitorRiskProperties;
import com.crypto.funding.contract.engine.EngineEntryAttemptPlan;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.contract.engine.EngineSummaryResponse;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.venue.InstrumentStatus;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.model.InstrumentMetadataEntity;
import com.crypto.funding.infrastructure.persistence.model.PositionEntity;
import com.crypto.funding.infrastructure.persistence.model.VenueTimingProfileEntity;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.InstrumentMetadataJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.PositionJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.VenueTimingProfileJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Service
public class MonitorEnginePlanService
{
    private static final Set<ArmedTradeState> ACTIVE_STATES = Set.of(
        ArmedTradeState.ARMED,
        ArmedTradeState.ENTRY_PENDING,
        ArmedTradeState.ENTRY_ATTEMPTED,
        ArmedTradeState.OPEN,
        ArmedTradeState.EXIT_PENDING
    );

    private final TradeQueryService tradeQueryService;
    private final FundingEventJpaRepository fundingEventRepository;
    private final InstrumentMetadataJpaRepository instrumentMetadataRepository;
    private final PositionJpaRepository positionRepository;
    private final VenueTimingProfileJpaRepository timingProfileRepository;
    private final MonitorEnginePlanProperties engineProperties;
    private final MonitorRiskProperties riskProperties;
    private final EngineEntryAttemptScheduleBuilder attemptScheduleBuilder;
    private final EnginePlanStatusCalculator statusCalculator;
    private final EnginePlanLookaheadFilter lookaheadFilter;
    private final EnginePlanSummaryFormatter summaryFormatter;
    private final VenueLatencyProbeService latencyProbeService;
    private final VenueProfileService venueProfileService;
    private final Clock clock;

    @Autowired
    public MonitorEnginePlanService(
        TradeQueryService tradeQueryService,
        FundingEventJpaRepository fundingEventRepository,
        InstrumentMetadataJpaRepository instrumentMetadataRepository,
        PositionJpaRepository positionRepository,
        VenueTimingProfileJpaRepository timingProfileRepository,
        MonitorEnginePlanProperties engineProperties,
        MonitorRiskProperties riskProperties,
        EngineEntryAttemptScheduleBuilder attemptScheduleBuilder,
        EnginePlanStatusCalculator statusCalculator,
        EnginePlanLookaheadFilter lookaheadFilter,
        EnginePlanSummaryFormatter summaryFormatter,
        VenueLatencyProbeService latencyProbeService,
        VenueProfileService venueProfileService
    )
    {
        this(
            tradeQueryService,
            fundingEventRepository,
            instrumentMetadataRepository,
            positionRepository,
            timingProfileRepository,
            engineProperties,
            riskProperties,
            attemptScheduleBuilder,
            statusCalculator,
            lookaheadFilter,
            summaryFormatter,
            latencyProbeService,
            venueProfileService,
            Clock.systemUTC()
        );
    }

    MonitorEnginePlanService(
        TradeQueryService tradeQueryService,
        FundingEventJpaRepository fundingEventRepository,
        InstrumentMetadataJpaRepository instrumentMetadataRepository,
        PositionJpaRepository positionRepository,
        VenueTimingProfileJpaRepository timingProfileRepository,
        MonitorEnginePlanProperties engineProperties,
        MonitorRiskProperties riskProperties,
        EngineEntryAttemptScheduleBuilder attemptScheduleBuilder,
        EnginePlanStatusCalculator statusCalculator,
        EnginePlanLookaheadFilter lookaheadFilter,
        EnginePlanSummaryFormatter summaryFormatter,
        VenueLatencyProbeService latencyProbeService,
        VenueProfileService venueProfileService,
        Clock clock
    )
    {
        this.tradeQueryService = tradeQueryService;
        this.fundingEventRepository = fundingEventRepository;
        this.instrumentMetadataRepository = instrumentMetadataRepository;
        this.positionRepository = positionRepository;
        this.timingProfileRepository = timingProfileRepository;
        this.engineProperties = engineProperties;
        this.riskProperties = riskProperties;
        this.attemptScheduleBuilder = attemptScheduleBuilder;
        this.statusCalculator = statusCalculator;
        this.lookaheadFilter = lookaheadFilter;
        this.summaryFormatter = summaryFormatter;
        this.latencyProbeService = latencyProbeService;
        this.venueProfileService = venueProfileService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EngineExecutionPlan> listPlans()
    {
        return listPlans( false );
    }

    @Transactional(readOnly = true)
    public List<EngineExecutionPlan> listPlans( boolean includeAll )
    {
        Instant now = Instant.now( clock );
        Predicate<ArmedTrade> stateFilter = engineProperties.isIncludeClosedTrades()
                                            ? trade -> true
                                            : trade -> ACTIVE_STATES.contains( trade.state() );

        return tradeQueryService.listArmedTrades( true )
                                .stream()
                                .filter( stateFilter )
                                .map( trade -> toPlan( trade, now ) )
                                .filter( plan -> lookaheadFilter.shouldInclude( plan, includeAll ) )
                                .sorted( Comparator.comparing( EngineExecutionPlan::nextActionAt, Comparator.nullsLast( Comparator.naturalOrder() ) )
                                                   .thenComparing( EngineExecutionPlan::armedTradeId ) )
                                .toList();
    }

    @Transactional(readOnly = true)
    public EngineExecutionPlan getPlan( Long armedTradeId )
    {
        Instant now = Instant.now( clock );
        ArmedTrade trade = tradeQueryService.getArmedTrade( armedTradeId );
        return toPlan( trade, now );
    }

    @Transactional(readOnly = true)
    public EngineSummaryResponse summary()
    {
        List<EngineExecutionPlan> plans = listPlans();
        Map<EnginePlanStatus, Long> breakdown = new EnumMap<>( EnginePlanStatus.class );
        for( EnginePlanStatus status : EnginePlanStatus.values() )
        {
            breakdown.put( status, 0L );
        }
        for( EngineExecutionPlan plan : plans )
        {
            breakdown.computeIfPresent( plan.status(), ( ignored, count ) -> count + 1L );
        }

        long actionable = plans.stream()
                               .filter( plan -> plan.status() == EnginePlanStatus.ENTRY_WINDOW || plan.status() == EnginePlanStatus.EXIT_WINDOW )
                               .count();

        return new EngineSummaryResponse(
            "monitor-engine-plans",
            "2.0.0",
            plans.size(),
            (int) actionable,
            Instant.now( clock ),
            breakdown
        );
    }

    private EngineExecutionPlan toPlan( ArmedTrade trade, Instant now )
    {
        FundingEventEntity fundingEvent = fundingEventRepository.findById( trade.fundingEventId() )
                                                               .orElseThrow( () -> new ResourceNotFoundException(
                                                                   "Событие фандинга не найдено для подготовленной сделки: " + trade.id()
                                                               ) );

        String venueNormalized = fundingEvent.getVenue() == null ? "" : fundingEvent.getVenue().trim().toLowerCase( Locale.ROOT );
        EnginePlanStatus status = riskProperties.disabledVenues().contains( venueNormalized )
                                  ? EnginePlanStatus.INVALID
                                  : statusCalculator.deriveStatus( trade, now );
        List<EngineEntryAttemptPlan> entryAttempts = attemptScheduleBuilder.build( trade, now );
        Instant nextActionAt = nextActionAt( trade, status, entryAttempts, now );
        Long millisUntilAction = nextActionAt == null ? null : Duration.between( now, nextActionAt ).toMillis();
        Long millisUntilFunding = Duration.between( now, fundingEvent.getFundingTime() ).toMillis();
        InstrumentMetadataEntity metadata = instrumentMetadataRepository
            .findByVenueAndCanonicalSymbolAndStatus( fundingEvent.getVenue(), fundingEvent.getSymbol(), InstrumentStatus.ACTIVE )
            .orElse( null );
        PositionEntity position = positionRepository.findFirstByArmedTradeIdOrderByCreatedAtDesc( trade.id() ).orElse( null );
        VenueTimingProfileEntity timing = timingProfileRepository
            .findFirstByVenueAndSymbolOrderBySampledAtDesc( fundingEvent.getVenue(), fundingEvent.getSymbol() )
            .orElse( null );

        VenueProfileService.VenueAccessProfile venueProfile = venueProfileService.getProfile( fundingEvent.getVenue() );
        String probeUrl = latencyProbeService.probeUrlFor( fundingEvent.getVenue(), venueProfile.mode() );

        return new EngineExecutionPlan(
            trade.id(),
            trade.fundingEventId(),
            fundingEvent.getVenue(),
            fundingEvent.getSymbol(),
            trade.intendedSide(),
            trade.notionalUsd(),
            trade.state(),
            fundingEvent.getFundingTime(),
            trade.plannedEntryAt(),
            trade.plannedExitAt(),
            trade.entryAttemptCount(),
            trade.entrySpacingMs(),
            trade.measuredEntryLatencyMs(),
            trade.manualLatencyAdjustmentMs(),
            trade.effectiveEntryLatencyMs(),
            entryAttempts,
            status,
            nextActionAt,
            millisUntilAction,
            millisUntilFunding,
            summaryFormatter.format( trade, fundingEvent, status ),
            metadata == null ? null : metadata.getVenueSymbol(),
            metadata == null ? null : metadata.getMinOrderQty(),
            metadata == null ? null : metadata.getQtyStep(),
            metadata == null ? null : metadata.getMinNotionalValue(),
            metadata == null ? null : metadata.getLastSyncedAt(),
            timing == null ? null : timing.getSampledAt(),
            positionQuantity( position ),
            position == null ? null : position.getEntryPrice(),
            trade.stopLossUsd(),
            trade.takeProfitUsd(),
            probeUrl,
            3,
            500L
        );
    }

    private static BigDecimal positionQuantity( PositionEntity position )
    {
        if( position == null )
        {
            return null;
        }
        return position.getQuantity();
    }

    private Instant nextActionAt( ArmedTrade trade, EnginePlanStatus status, List<EngineEntryAttemptPlan> entryAttempts, Instant now )
    {
        return switch( status )
        {
            case WAITING_ENTRY, OVERDUE -> entryAttempts.stream()
                                                        .map( EngineEntryAttemptPlan::triggerAt )
                                                        .min( Instant::compareTo )
                                                        .orElse( attemptScheduleBuilder.firstTriggerAt( trade ) );
            case ENTRY_WINDOW -> entryAttempts.stream()
                                              .map( EngineEntryAttemptPlan::triggerAt )
                                              .filter( trigger -> !trigger.isBefore( now ) )
                                              .min( Instant::compareTo )
                                              .orElseGet( () -> entryAttempts.stream()
                                                                              .map( EngineEntryAttemptPlan::triggerAt )
                                                                              .max( Instant::compareTo )
                                                                              .orElse( attemptScheduleBuilder.firstTriggerAt( trade ) ) );
            case WAITING_EXIT, EXIT_WINDOW -> trade.plannedExitAt();
            case CLOSED, INVALID -> null;
        };
    }
}
