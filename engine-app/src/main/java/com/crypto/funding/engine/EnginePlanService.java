package com.crypto.funding.engine;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.query.TradeQueryService;
import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.infrastructure.persistence.mapper.FundingEventMapper;
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
public class EnginePlanService
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
    private final EngineProperties engineProperties;
    private final Clock clock;

    @Autowired
    public EnginePlanService(
        TradeQueryService tradeQueryService,
        FundingEventJpaRepository fundingEventRepository,
        EngineProperties engineProperties
    )
    {
        this( tradeQueryService, fundingEventRepository, engineProperties, Clock.systemUTC() );
    }

    EnginePlanService(
        TradeQueryService tradeQueryService,
        FundingEventJpaRepository fundingEventRepository,
        EngineProperties engineProperties,
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
        Instant now = Instant.now( clock );
        Predicate<ArmedTrade> stateFilter = engineProperties.isIncludeClosedTrades()
                                            ? trade -> true
                                            : trade -> ACTIVE_STATES.contains( trade.state() );

        return tradeQueryService.listArmedTrades()
                                .stream()
                                .filter( stateFilter )
                                .map( trade -> toPlan( trade, now ) )
                                .filter( this::withinLookaheadWindow )
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
            "engine-app",
            "2.0.0",
            plans.size(),
            (int) actionable,
            Instant.now( clock ),
            breakdown
        );
    }

    private EngineExecutionPlan toPlan( ArmedTrade trade, Instant now )
    {
        FundingEvent fundingEvent = fundingEventRepository.findById( trade.fundingEventId() )
                                                          .map( FundingEventMapper::toDomain )
                                                          .orElseThrow( () -> new ResourceNotFoundException(
                                                              "FundingEvent not found for ArmedTrade: " + trade.id()
                                                          ) );

        EnginePlanStatus status = deriveStatus( trade, now );
        Instant nextActionAt = nextActionAt( trade, status );
        Long millisUntilAction = nextActionAt == null ? null : Duration.between( now, nextActionAt ).toMillis();
        Long millisUntilFunding = Duration.between( now, fundingEvent.fundingTime() ).toMillis();

        return new EngineExecutionPlan(
            trade.id(),
            trade.fundingEventId(),
            fundingEvent.venue(),
            fundingEvent.symbol(),
            trade.intendedSide(),
            trade.notionalUsd(),
            trade.state(),
            fundingEvent.fundingTime(),
            trade.plannedEntryAt(),
            trade.plannedExitAt(),
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

        Instant entry = trade.plannedEntryAt();
        Instant exit = trade.plannedExitAt();
        Instant overdueThreshold = entry.plusSeconds( engineProperties.getOverdueGraceSeconds() );

        if( trade.state() == ArmedTradeState.ARMED || trade.state() == ArmedTradeState.ENTRY_PENDING || trade.state() == ArmedTradeState.ENTRY_ATTEMPTED )
        {
            if( now.isBefore( entry ) )
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

    private Instant nextActionAt( ArmedTrade trade, EnginePlanStatus status )
    {
        return switch( status )
        {
            case WAITING_ENTRY, ENTRY_WINDOW, OVERDUE -> trade.plannedEntryAt();
            case WAITING_EXIT, EXIT_WINDOW -> trade.plannedExitAt();
            case CLOSED, INVALID -> null;
        };
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

    private String summaryText( ArmedTrade trade, FundingEvent fundingEvent, EnginePlanStatus status )
    {
        return switch( status )
        {
            case WAITING_ENTRY -> "Waiting to enter " + fundingEvent.symbol() + " on " + fundingEvent.venue();
            case ENTRY_WINDOW -> "Entry window active for " + fundingEvent.symbol() + " (" + trade.intendedSide() + ")";
            case WAITING_EXIT -> "Position should be monitored until planned exit";
            case EXIT_WINDOW -> "Exit window active for " + fundingEvent.symbol();
            case OVERDUE -> "Entry window missed and requires operator attention";
            case CLOSED -> "Trade already closed";
            case INVALID -> "Trade plan is incomplete for execution";
        };
    }
}
