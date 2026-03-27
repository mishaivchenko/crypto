package com.crypto.funding.api;

import com.crypto.funding.api.dto.ArmedTradeResponse;
import com.crypto.funding.api.dto.CreateArmedTradeRequest;
import com.crypto.funding.application.query.TradeQueryService;
import com.crypto.funding.application.trade.ArmedTradeCommandService;
import com.crypto.funding.application.trade.CreateArmedTradeCommand;
import com.crypto.funding.domain.trade.ArmedTrade;
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

    public ArmedTradeController(
        ArmedTradeCommandService armedTradeCommandService,
        TradeQueryService tradeQueryService
    )
    {
        this.armedTradeCommandService = armedTradeCommandService;
        this.tradeQueryService = tradeQueryService;
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

    private ArmedTradeResponse toResponse( ArmedTrade trade )
    {
        return new ArmedTradeResponse(
            trade.id(),
            trade.fundingEventId(),
            trade.notionalUsd(),
            trade.intendedSide(),
            trade.plannedEntryAt(),
            trade.plannedExitAt(),
            trade.state(),
            trade.notes(),
            trade.createdAt(),
            trade.updatedAt()
        );
    }
}
