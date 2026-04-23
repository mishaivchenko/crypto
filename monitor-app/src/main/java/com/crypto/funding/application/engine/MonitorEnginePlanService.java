package com.crypto.funding.application.engine;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.engine.planning.EngineEntryAttemptScheduleBuilder;
import com.crypto.funding.application.engine.planning.EnginePlanLookaheadFilter;
import com.crypto.funding.application.engine.planning.EnginePlanStatusCalculator;
import com.crypto.funding.application.engine.planning.EnginePlanSummaryFormatter;
import com.crypto.funding.application.query.TradeQueryService;
import com.crypto.funding.config.MonitorEnginePlanProperties;
import com.crypto.funding.contract.engine.EngineEntryAttemptPlan;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.contract.engine.EngineSummaryResponse;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
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
    private final MonitorEnginePlanProperties engineProperties;
    private final EngineEntryAttemptScheduleBuilder attemptScheduleBuilder;
    private final EnginePlanStatusCalculator statusCalculator;
    private final EnginePlanLookaheadFilter lookaheadFilter;
    private final EnginePlanSummaryFormatter summaryFormatter;
    private final Clock clock;

    @Autowired
    public MonitorEnginePlanService(
        TradeQueryService tradeQueryService,
        FundingEventJpaRepository fundingEventRepository,
        MonitorEnginePlanProperties engineProperties,
        EngineEntryAttemptScheduleBuilder attemptScheduleBuilder,
        EnginePlanStatusCalculator statusCalculator,
        EnginePlanLookaheadFilter lookaheadFilter,
        EnginePlanSummaryFormatter summaryFormatter
    )
    {
        this(
            tradeQueryService,
            fundingEventRepository,
            engineProperties,
            attemptScheduleBuilder,
            statusCalculator,
            lookaheadFilter,
            summaryFormatter,
            Clock.systemUTC()
        );
    }

    MonitorEnginePlanService(
        TradeQueryService tradeQueryService,
        FundingEventJpaRepository fundingEventRepository,
        MonitorEnginePlanProperties engineProperties,
        EngineEntryAttemptScheduleBuilder attemptScheduleBuilder,
        EnginePlanStatusCalculator statusCalculator,
        EnginePlanLookaheadFilter lookaheadFilter,
        EnginePlanSummaryFormatter summaryFormatter,
        Clock clock
    )
    {
        this.tradeQueryService = tradeQueryService;
        this.fundingEventRepository = fundingEventRepository;
        this.engineProperties = engineProperties;
        this.attemptScheduleBuilder = attemptScheduleBuilder;
        this.statusCalculator = statusCalculator;
        this.lookaheadFilter = lookaheadFilter;
        this.summaryFormatter = summaryFormatter;
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

        EnginePlanStatus status = statusCalculator.deriveStatus( trade, now );
        List<EngineEntryAttemptPlan> entryAttempts = attemptScheduleBuilder.build( trade, now );
        Instant nextActionAt = nextActionAt( trade, status, entryAttempts, now );
        Long millisUntilAction = nextActionAt == null ? null : Duration.between( now, nextActionAt ).toMillis();
        Long millisUntilFunding = Duration.between( now, fundingEvent.getFundingTime() ).toMillis();

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
            summaryFormatter.format( trade, fundingEvent, status )
        );
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
