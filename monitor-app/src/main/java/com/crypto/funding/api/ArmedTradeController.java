package com.crypto.funding.api;

import com.crypto.funding.api.dto.ArmedTradeResponse;
import com.crypto.funding.api.dto.CreateArmedTradeRequest;
import com.crypto.funding.api.dto.EngineRunOnceResponse;
import com.crypto.funding.api.dto.TradeJournalEntryResponse;
import com.crypto.funding.api.dto.TradeOutcomeResponse;
import com.crypto.funding.api.dto.TradePositionResponse;
import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.application.execution.PositionQueryService;
import com.crypto.funding.application.execution.TradeOutcomeQueryService;
import com.crypto.funding.application.monitor.EngineControlService;
import com.crypto.funding.application.query.TradeQueryService;
import com.crypto.funding.application.trade.ArmedTradeCommandService;
import com.crypto.funding.application.trade.CreateArmedTradeCommand;
import com.crypto.funding.application.trade.TradeJournalService;
import com.crypto.funding.application.event.FundingEventQueryService;
import com.crypto.funding.contract.engine.EngineExecutionTargetPhase;
import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeJournalEntry;
import com.crypto.funding.domain.trade.TradeJournalEntityType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/armed-trades")
public class ArmedTradeController
{
    private static final Set<ArmedTradeState> CLOSEABLE_STATES = Set.of(
        ArmedTradeState.OPEN,
        ArmedTradeState.EXIT_PENDING
    );

    private final ArmedTradeCommandService armedTradeCommandService;
    private final TradeQueryService tradeQueryService;
    private final TradeJournalService tradeJournalService;
    private final FundingEventQueryService fundingEventQueryService;
    private final PositionQueryService positionQueryService;
    private final TradeOutcomeQueryService tradeOutcomeQueryService;
    private final EngineControlService engineControlService;

    public ArmedTradeController(
        ArmedTradeCommandService armedTradeCommandService,
        TradeQueryService tradeQueryService,
        TradeJournalService tradeJournalService,
        FundingEventQueryService fundingEventQueryService,
        PositionQueryService positionQueryService,
        TradeOutcomeQueryService tradeOutcomeQueryService,
        EngineControlService engineControlService
    )
    {
        this.armedTradeCommandService = armedTradeCommandService;
        this.tradeQueryService = tradeQueryService;
        this.tradeJournalService = tradeJournalService;
        this.fundingEventQueryService = fundingEventQueryService;
        this.positionQueryService = positionQueryService;
        this.tradeOutcomeQueryService = tradeOutcomeQueryService;
        this.engineControlService = engineControlService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArmedTradeResponse create( @Valid @RequestBody CreateArmedTradeRequest request )
    {
        ArmedTrade created = armedTradeCommandService.create(
            new CreateArmedTradeCommand(
                request.fundingEventId(),
                request.notionalUsd(),
                request.intendedSide(),
                request.plannedEntryAt(),
                request.plannedExitAt(),
                request.entryAttemptCount(),
                request.entrySpacingMs(),
                request.manualLatencyAdjustmentMs(),
                request.notes()
            )
        );
        return toResponse( created );
    }

    @GetMapping
    public List<ArmedTradeResponse> list( @RequestParam(defaultValue = "false") boolean includeHistorical )
    {
        return tradeQueryService.listArmedTrades( includeHistorical ).stream().map( this::toResponse ).toList();
    }

    @GetMapping("/{id}")
    public ArmedTradeResponse getById( @PathVariable Long id )
    {
        return toResponse( tradeQueryService.getArmedTrade( id ) );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel( @PathVariable Long id )
    {
        armedTradeCommandService.cancel( id );
    }

    @PostMapping("/{id}/close")
    public EngineRunOnceResponse close( @PathVariable Long id )
    {
        ArmedTrade trade = tradeQueryService.getArmedTrade( id );
        if( !CLOSEABLE_STATES.contains( trade.state() ) )
        {
            throw new DomainValidationException( "Нельзя закрыть сделку в статусе " + trade.state() + ". Закрытие возможно только из OPEN или EXIT_PENDING." );
        }
        return EngineRunOnceResponse.from( engineControlService.runTarget( id, EngineExecutionTargetPhase.EXIT, true ) );
    }

    @GetMapping("/{id}/journal")
    public List<TradeJournalEntryResponse> journal( @PathVariable Long id )
    {
        return tradeJournalService.list( TradeJournalEntityType.ARMED_TRADE, id ).stream().map( this::toJournalResponse ).toList();
    }

    @GetMapping("/{id}/position")
    public ResponseEntity<TradePositionResponse> position( @PathVariable Long id )
    {
        return positionQueryService.findByArmedTrade( id )
                                   .map( p -> ResponseEntity.ok( new TradePositionResponse(
                                       p.state(), p.quantity(), p.entryPrice(), p.exitPrice(), p.openedAt(), p.closedAt()
                                   ) ) )
                                   .orElse( ResponseEntity.noContent().build() );
    }

    @GetMapping("/{id}/outcome")
    public ResponseEntity<TradeOutcomeResponse> outcome( @PathVariable Long id )
    {
        return tradeOutcomeQueryService.findByArmedTrade( id )
                                       .map( o -> ResponseEntity.ok( new TradeOutcomeResponse(
                                           o.grossPnlUsd(), o.netPnlUsd(), o.feesUsd(), o.outcomeCode(), o.evaluatedAt()
                                       ) ) )
                                       .orElse( ResponseEntity.noContent().build() );
    }

    private ArmedTradeResponse toResponse( ArmedTrade trade )
    {
        FundingEvent fundingEvent = fundingEventQueryService.getFundingEvent( trade.fundingEventId() );
        return new ArmedTradeResponse(
            trade.id(),
            trade.fundingEventId(),
            fundingEvent.signalCandidateId(),
            fundingEvent.venue(),
            fundingEvent.symbol(),
            fundingEvent.fundingTime(),
            trade.notionalUsd(),
            trade.intendedSide(),
            trade.plannedEntryAt(),
            trade.plannedExitAt(),
            trade.armedAt(),
            trade.eventAgeMsAtArm(),
            trade.entryLeadMs(),
            trade.exitLeadMs(),
            trade.entryAttemptCount(),
            trade.entrySpacingMs(),
            trade.measuredEntryLatencyMs(),
            trade.manualLatencyAdjustmentMs(),
            trade.effectiveEntryLatencyMs(),
            trade.armSource(),
            trade.state(),
            trade.notes(),
            trade.mode() == null ? null : trade.mode().propertyValue(),
            trade.createdAt(),
            trade.updatedAt()
        );
    }

    private TradeJournalEntryResponse toJournalResponse( TradeJournalEntry entry )
    {
        return new TradeJournalEntryResponse(
            entry.id(),
            entry.entityType(),
            entry.entityId(),
            entry.eventCode(),
            entry.oldState(),
            entry.newState(),
            entry.actorType(),
            entry.actorRef(),
            entry.note(),
            entry.createdAt()
        );
    }
}
