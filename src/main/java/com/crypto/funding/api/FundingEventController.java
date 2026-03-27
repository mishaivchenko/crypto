package com.crypto.funding.api;

import com.crypto.funding.api.dto.CreateFundingEventRequest;
import com.crypto.funding.api.dto.FundingEventResponse;
import com.crypto.funding.application.event.CreateFundingEventCommand;
import com.crypto.funding.application.event.FundingEventCommandService;
import com.crypto.funding.domain.event.FundingEvent;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/funding-events")
public class FundingEventController
{
    private final FundingEventCommandService fundingEventCommandService;

    public FundingEventController( FundingEventCommandService fundingEventCommandService )
    {
        this.fundingEventCommandService = fundingEventCommandService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FundingEventResponse create( @Valid @RequestBody CreateFundingEventRequest request )
    {
        FundingEvent created = fundingEventCommandService.create(
            new CreateFundingEventCommand(
                request.venue(),
                request.symbol(),
                request.fundingTime(),
                request.fundingRatePct(),
                request.sourceType(),
                request.sourceRef()
            )
        );
        return toResponse( created );
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
            event.discoveredAt(),
            event.createdAt(),
            event.updatedAt()
        );
    }
}
