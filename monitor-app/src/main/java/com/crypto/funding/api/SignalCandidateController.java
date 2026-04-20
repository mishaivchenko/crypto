package com.crypto.funding.api;

import com.crypto.funding.api.dto.ApproveCandidateRequest;
import com.crypto.funding.api.dto.CandidateListItemResponse;
import com.crypto.funding.api.dto.CandidateResponse;
import com.crypto.funding.api.dto.RejectCandidateRequest;
import com.crypto.funding.application.candidate.ApproveSignalCandidateCommand;
import com.crypto.funding.application.candidate.RejectSignalCandidateCommand;
import com.crypto.funding.application.candidate.SignalCandidateLifecycleService;
import com.crypto.funding.application.candidate.SignalCandidateQueryService;
import com.crypto.funding.application.candidate.SignalCandidateReviewService;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/candidates")
public class SignalCandidateController
{
    private final SignalCandidateQueryService queryService;
    private final SignalCandidateReviewService reviewService;
    private final SignalCandidateLifecycleService lifecycleService;

    public SignalCandidateController(
        SignalCandidateQueryService queryService,
        SignalCandidateReviewService reviewService,
        SignalCandidateLifecycleService lifecycleService
    )
    {
        this.queryService = queryService;
        this.reviewService = reviewService;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping
    public Page<CandidateListItemResponse> list(
        @RequestParam(required = false) SignalCandidateStatus status,
        @RequestParam(required = false) String venue,
        @RequestParam(required = false) String symbol,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant detectedFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant detectedTo,
        Pageable pageable
    )
    {
        return queryService.listCandidates( status, venue, symbol, detectedFrom, detectedTo, pageable ).map( this::toListItem );
    }

    @GetMapping("/{id}")
    public CandidateResponse get( @PathVariable Long id )
    {
        return toResponse( queryService.getCandidate( id ) );
    }

    @PostMapping("/{id}/approve")
    @ResponseStatus(HttpStatus.OK)
    public CandidateResponse approve( @PathVariable Long id, @Valid @RequestBody(required = false) ApproveCandidateRequest request )
    {
        ApproveCandidateRequest safeRequest = request == null
                                              ? new ApproveCandidateRequest( null, null, null, null, null )
                                              : request;
        return toResponse( reviewService.approve( new ApproveSignalCandidateCommand(
            id,
            safeRequest.venue(),
            safeRequest.symbol(),
            safeRequest.fundingTime(),
            safeRequest.fundingRatePct(),
            safeRequest.reviewNotes()
        ) ) );
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.OK)
    public CandidateResponse reject( @PathVariable Long id, @Valid @RequestBody RejectCandidateRequest request )
    {
        return toResponse( reviewService.reject( new RejectSignalCandidateCommand( id, request.reviewNotes() ) ) );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public CandidateResponse delete( @PathVariable Long id, @RequestParam(required = false) String note )
    {
        return toResponse( lifecycleService.deleteCandidate( id, note ) );
    }

    private CandidateListItemResponse toListItem( SignalCandidate candidate )
    {
        return new CandidateListItemResponse(
            candidate.id(),
            candidate.sourceType(),
            candidate.sourceChatId(),
            candidate.sourceMessageId(),
            candidate.sourceVenue(),
            candidate.rawSymbol(),
            candidate.normalizedSymbol(),
            candidate.venueHints(),
            candidate.detectedAt(),
            candidate.status(),
            candidate.reviewDecision(),
            candidate.sourceFundingTime(),
            candidate.sourceFundingRatePct(),
            candidate.fundingEventId(),
            candidate.normalizationFailureReason()
        );
    }

    private CandidateResponse toResponse( SignalCandidate candidate )
    {
        CandidateSuggestion suggestion = buildSuggestion( candidate );
        return new CandidateResponse(
            candidate.id(),
            candidate.sourceType(),
            candidate.sourceChatId(),
            candidate.sourceMessageId(),
            candidate.rawPayload(),
            candidate.sourceVenue(),
            candidate.rawSymbol(),
            candidate.normalizedSymbol(),
            candidate.venueHints(),
            candidate.detectedAt(),
            candidate.status(),
            candidate.reviewedAt(),
            candidate.reviewDecision(),
            candidate.reviewNotes(),
            candidate.normalizationFailureReason(),
            candidate.sourceFundingTime(),
            candidate.sourceFundingRatePct(),
            candidate.fundingEventId(),
            suggestion.venue(),
            suggestion.fundingTime(),
            suggestion.fundingRatePct(),
            candidate.createdAt(),
            candidate.updatedAt()
        );
    }

    private CandidateSuggestion buildSuggestion( SignalCandidate candidate )
    {
        List<String> preferredVenues = new java.util.ArrayList<>();
        if( candidate.sourceVenue() != null && !candidate.sourceVenue().isBlank() )
        {
            preferredVenues.add( candidate.sourceVenue() );
        }
        preferredVenues.addAll( candidate.venueHints() );

        for( String venue : preferredVenues.stream().filter( value -> value != null && !value.isBlank() ).distinct().toList() )
        {
            return new CandidateSuggestion( venue, candidate.sourceFundingTime(), candidate.sourceFundingRatePct() );
        }

        String fallbackVenue = preferredVenues.stream().filter( value -> value != null && !value.isBlank() ).findFirst().orElse( null );
        return new CandidateSuggestion( fallbackVenue, candidate.sourceFundingTime(), candidate.sourceFundingRatePct() );
    }

    private record CandidateSuggestion(
        String venue,
        Instant fundingTime,
        java.math.BigDecimal fundingRatePct
    )
    {
    }
}
