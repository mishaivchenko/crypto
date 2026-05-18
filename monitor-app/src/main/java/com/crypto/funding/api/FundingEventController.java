package com.crypto.funding.api;

import com.crypto.funding.api.dto.ArmFundingEventRequest;
import com.crypto.funding.api.dto.FundingEventListItemResponse;
import com.crypto.funding.api.dto.FundingEventResponse;
import com.crypto.funding.application.event.FundingEventArmService;
import com.crypto.funding.application.event.FundingEventQueryService;
import com.crypto.funding.application.trade.TradeJournalService;
import com.crypto.funding.application.venue.InstrumentRegistryService;
import com.crypto.funding.api.dto.TradeJournalEntryResponse;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.TradeJournalEntry;
import com.crypto.funding.domain.trade.TradeJournalEntityType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/funding-events")
public class FundingEventController
{
    private final FundingEventQueryService fundingEventQueryService;
    private final FundingEventArmService fundingEventArmService;
    private final TradeJournalService tradeJournalService;
    private final InstrumentRegistryService instrumentRegistryService;

    public FundingEventController(
        FundingEventQueryService fundingEventQueryService,
        FundingEventArmService fundingEventArmService,
        TradeJournalService tradeJournalService,
        InstrumentRegistryService instrumentRegistryService
    )
    {
        this.fundingEventQueryService = fundingEventQueryService;
        this.fundingEventArmService = fundingEventArmService;
        this.tradeJournalService = tradeJournalService;
        this.instrumentRegistryService = instrumentRegistryService;
    }

    @GetMapping
    public Page<FundingEventListItemResponse> list(
        @RequestParam(required = false) FundingEventStatus status,
        @RequestParam(required = false) String venue,
        @RequestParam(required = false) String symbol,
        @RequestParam(required = false) String sourceType,
        @RequestParam(required = false) Long candidateId,
        Pageable pageable
    )
    {
        return fundingEventQueryService.listFundingEvents( status, venue, symbol, sourceType, candidateId, pageable )
                                       .map( this::toListItem );
    }

    @GetMapping("/{id}")
    public FundingEventResponse getById( @PathVariable Long id )
    {
        return toResponse( fundingEventQueryService.getFundingEvent( id ) );
    }

    @PostMapping("/{id}/arm")
    @ResponseStatus(HttpStatus.CREATED)
    public com.crypto.funding.api.dto.ArmedTradeResponse arm(
        @PathVariable Long id,
        @Valid @RequestBody ArmFundingEventRequest request
    )
    {
        ArmedTrade trade = fundingEventArmService.arm(
            id,
            new com.crypto.funding.application.event.ArmFundingEventCommand(
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
        FundingEvent event = fundingEventQueryService.getFundingEvent( trade.fundingEventId() );
        String venueSymbol = instrumentRegistryService
            .resolveVenueSymbol( event.venue(), event.symbol() )
            .orElse( null );
        return new com.crypto.funding.api.dto.ArmedTradeResponse(
            trade.id(),
            trade.fundingEventId(),
            event.signalCandidateId(),
            event.venue(),
            event.symbol(),
            venueSymbol,
            event.fundingTime(),
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
            trade.stopLossUsd(),
            trade.takeProfitUsd(),
            trade.createdAt(),
            trade.updatedAt(),
            trade.warmupP50Ms(),
            trade.warmupP95Ms(),
            trade.warmupFallbackUsed(),
            trade.warmupDoneAt()
        );
    }

    @GetMapping("/{id}/journal")
    public java.util.List<TradeJournalEntryResponse> journal( @PathVariable Long id )
    {
        return tradeJournalService.list( TradeJournalEntityType.FUNDING_EVENT, id ).stream().map( this::toJournalResponse ).toList();
    }

    private FundingEventResponse toResponse( FundingEvent event )
    {
        return new FundingEventResponse(
            event.id(),
            event.venue(),
            event.symbol(),
            event.fundingTime(),
            event.fundingRatePct(),
            event.status(),
            event.sourceType(),
            event.sourceRef(),
            event.signalCandidateId(),
            event.armedTradeId(),
            event.discoveredAt(),
            event.createdAt(),
            event.updatedAt()
        );
    }

    private FundingEventListItemResponse toListItem( FundingEvent event )
    {
        return new FundingEventListItemResponse(
            event.id(),
            event.venue(),
            event.symbol(),
            event.fundingTime(),
            event.fundingRatePct(),
            event.status(),
            event.sourceType(),
            event.signalCandidateId(),
            event.armedTradeId(),
            event.discoveredAt()
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
