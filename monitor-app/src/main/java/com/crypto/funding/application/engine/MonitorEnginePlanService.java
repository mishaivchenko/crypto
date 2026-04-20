package com.crypto.funding.application.engine;

import com.crypto.funding.application.ResourceNotFoundException;
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
    private final Clock clock;

    @Autowired
    public MonitorEnginePlanService(
        TradeQueryService tradeQueryService,
        FundingEventJpaRepository fundingEventRepository,
        MonitorEnginePlanProperties engineProperties
    )
    {
        this( tradeQueryService, fundingEventRepository, engineProperties, Clock.systemUTC() );
    }

    MonitorEnginePlanService(
        TradeQueryService tradeQueryService,
        FundingEventJpaRepository fundingEventRepository,
        MonitorEnginePlanProperties engineProperties,
        Clock clock
    )
    {
        this.tradeQueryService = tradeQueryService;
        this.fundingEventRepository = fundingEventRepository;
        this.engineProperties = engineProperties;
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

        return tradeQueryService.listArmedTrades()
                                .stream()
                                .filter( stateFilter )
                                .map( trade -> toPlan( trade, now ) )
                                .filter( plan -> includeAll || withinLookaheadWindow( plan ) )
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

        EnginePlanStatus status = deriveStatus( trade, now );
        List<EngineEntryAttemptPlan> entryAttempts = entryAttempts( trade, now );
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
            summaryText( trade, fundingEvent, status )
        );
    }

    private EnginePlanStatus deriveStatus( ArmedTrade trade, Instant now )
    {
        if( trade.state() == ArmedTradeState.CLOSED )
        {
            return EnginePlanStatus.CLOSED;
        }
        if( trade.state() == ArmedTradeState.CANCELLED || trade.state() == ArmedTradeState.FAILED )
        {
            return EnginePlanStatus.INVALID;
        }
        if( trade.plannedEntryAt() == null )
        {
            return EnginePlanStatus.INVALID;
        }

        Instant firstTrigger = firstEntryTriggerAt( trade );
        Instant lastTarget = lastEntryTargetAt( trade );
        Instant exit = trade.plannedExitAt();
        Instant overdueThreshold = lastTarget.plusSeconds( engineProperties.getOverdueGraceSeconds() );

        if( trade.state() == ArmedTradeState.ARMED || trade.state() == ArmedTradeState.ENTRY_PENDING || trade.state() == ArmedTradeState.ENTRY_ATTEMPTED )
        {
            if( now.isBefore( firstTrigger ) )
            {
                return EnginePlanStatus.WAITING_ENTRY;
            }
            if( exit != null && now.isAfter( exit ) )
            {
                return EnginePlanStatus.OVERDUE;
            }
            if( now.isAfter( overdueThreshold ) )
            {
                return EnginePlanStatus.OVERDUE;
            }
            return EnginePlanStatus.ENTRY_WINDOW;
        }

        if( trade.state() == ArmedTradeState.OPEN || trade.state() == ArmedTradeState.EXIT_PENDING )
        {
            if( exit == null )
            {
                return EnginePlanStatus.INVALID;
            }
            if( now.isBefore( exit ) )
            {
                return EnginePlanStatus.WAITING_EXIT;
            }
            return EnginePlanStatus.EXIT_WINDOW;
        }

        return EnginePlanStatus.INVALID;
    }

    private Instant nextActionAt( ArmedTrade trade, EnginePlanStatus status, List<EngineEntryAttemptPlan> entryAttempts, Instant now )
    {
        return switch( status )
        {
            case WAITING_ENTRY, OVERDUE -> entryAttempts.stream()
                                                        .map( EngineEntryAttemptPlan::triggerAt )
                                                        .min( Instant::compareTo )
                                                        .orElse( firstEntryTriggerAt( trade ) );
            case ENTRY_WINDOW -> entryAttempts.stream()
                                              .map( EngineEntryAttemptPlan::triggerAt )
                                              .filter( trigger -> !trigger.isBefore( now ) )
                                              .min( Instant::compareTo )
                                              .orElseGet( () -> entryAttempts.stream()
                                                                              .map( EngineEntryAttemptPlan::triggerAt )
                                                                              .max( Instant::compareTo )
                                                                              .orElse( firstEntryTriggerAt( trade ) ) );
            case WAITING_EXIT, EXIT_WINDOW -> trade.plannedExitAt();
            case CLOSED, INVALID -> null;
        };
    }

    private List<EngineEntryAttemptPlan> entryAttempts( ArmedTrade trade, Instant now )
    {
        if( trade.plannedEntryAt() == null )
        {
            return List.of();
        }

        int attempts = trade.entryAttemptCount() == null ? 1 : trade.entryAttemptCount();
        long spacingMs = trade.entrySpacingMs() == null ? 0L : trade.entrySpacingMs();
        long effectiveLatencyMs = trade.effectiveEntryLatencyMs() == null ? 0L : trade.effectiveEntryLatencyMs();

        return java.util.stream.IntStream.range( 0, attempts )
                                         .mapToObj( index -> {
                                             long offsetMs = spacingMs * index;
                                             Instant targetEntryAt = trade.plannedEntryAt().plusMillis( offsetMs );
                                             Instant triggerAt = targetEntryAt.minusMillis( effectiveLatencyMs );
                                             return new EngineEntryAttemptPlan(
                                                 index + 1,
                                                 targetEntryAt,
                                                 triggerAt,
                                                 Duration.between( now, triggerAt ).toMillis(),
                                                 offsetMs,
                                                 effectiveLatencyMs
                                             );
                                         } )
                                         .toList();
    }

    private Instant firstEntryTriggerAt( ArmedTrade trade )
    {
        long effectiveLatencyMs = trade.effectiveEntryLatencyMs() == null ? 0L : trade.effectiveEntryLatencyMs();
        return trade.plannedEntryAt().minusMillis( effectiveLatencyMs );
    }

    private Instant lastEntryTargetAt( ArmedTrade trade )
    {
        int attempts = trade.entryAttemptCount() == null ? 1 : trade.entryAttemptCount();
        long spacingMs = trade.entrySpacingMs() == null ? 0L : trade.entrySpacingMs();
        return trade.plannedEntryAt().plusMillis( spacingMs * Math.max( 0, attempts - 1 ) );
    }

    private boolean withinLookaheadWindow( EngineExecutionPlan plan )
    {
        if( plan.status() == EnginePlanStatus.ENTRY_WINDOW || plan.status() == EnginePlanStatus.EXIT_WINDOW || plan.status() == EnginePlanStatus.OVERDUE )
        {
            return true;
        }
        if( plan.millisUntilAction() == null )
        {
            return false;
        }
        long lookaheadMillis = Duration.ofMinutes( engineProperties.getLookaheadMinutes() ).toMillis();
        return plan.millisUntilAction() <= lookaheadMillis;
    }

    private String summaryText( ArmedTrade trade, FundingEventEntity fundingEvent, EnginePlanStatus status )
    {
        return switch( status )
        {
            case WAITING_ENTRY -> "Ожидаем вход по " + fundingEvent.getSymbol() + " на " + fundingEvent.getVenue();
            case ENTRY_WINDOW -> "Окно входа активно для " + fundingEvent.getSymbol() + " (" + trade.intendedSide() + ")";
            case WAITING_EXIT -> "Сделка ждёт планового выхода";
            case EXIT_WINDOW -> "Окно выхода активно для " + fundingEvent.getSymbol();
            case OVERDUE -> "Окно входа пропущено, нужен разбор оператора";
            case CLOSED -> "Сделка уже закрыта";
            case INVALID -> "План сделки неполный и не готов к исполнению";
        };
    }
}
