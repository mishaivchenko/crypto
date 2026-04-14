package com.crypto.funding.api;

import com.crypto.funding.api.dto.ArmedTradeResponse;
import com.crypto.funding.api.dto.CreateArmedTradeRequest;
import com.crypto.funding.api.dto.TradeJournalEntryResponse;
import com.crypto.funding.application.query.TradeQueryService;
import com.crypto.funding.application.trade.ArmedTradeCommandService;
import com.crypto.funding.application.trade.CreateArmedTradeCommand;
import com.crypto.funding.application.trade.TradeJournalService;
import com.crypto.funding.application.event.FundingEventQueryService;
import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.TradeJournalEntry;
import com.crypto.funding.domain.trade.TradeJournalEntityType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/armed-trades")
public class ArmedTradeController
{
    private final ArmedTradeCommandService armedTradeCommandService;
    private final TradeQueryService tradeQueryService;
    private final TradeJournalService tradeJournalService;
    private final FundingEventQueryService fundingEventQueryService;

    public ArmedTradeController(
        ArmedTradeCommandService armedTradeCommandService,
        TradeQueryService tradeQueryService,
        TradeJournalService tradeJournalService,
        FundingEventQueryService fundingEventQueryService
    )
    {
        this.armedTradeCommandService = armedTradeCommandService;
        this.tradeQueryService = tradeQueryService;
        this.tradeJournalService = tradeJournalService;
        this.fundingEventQueryService = fundingEventQueryService;
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
                request.notes()
            )
        );
        return toResponse( created );
    }

    @GetMapping
    public List<ArmedTradeResponse> list()
    {
        return tradeQueryService.listArmedTrades().stream().map( this::toResponse ).toList();
    }

    @GetMapping("/{id}")
    public ArmedTradeResponse getById( @PathVariable Long id )
    {
        return toResponse( tradeQueryService.getArmedTrade( id ) );
    }

    @GetMapping("/{id}/journal")
    public List<TradeJournalEntryResponse> journal( @PathVariable Long id )
    {
        return tradeJournalService.list( TradeJournalEntityType.ARMED_TRADE, id ).stream().map( this::toJournalResponse ).toList();
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
            trade.armSource(),
            trade.state(),
            trade.notes(),
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
