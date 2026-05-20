package com.crypto.funding.application.candidate;

import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.event.CreateFundingEventCommand;
import com.crypto.funding.application.event.FundingEventCommandService;
import com.crypto.funding.domain.candidate.ReviewDecision;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.domain.trade.TradeJournalActorType;
import com.crypto.funding.domain.trade.TradeJournalEntityType;
import com.crypto.funding.domain.trade.TradeJournalEventCode;
import com.crypto.funding.application.trade.TradeJournalService;
import com.crypto.funding.infrastructure.persistence.mapper.SignalCandidateMapper;
import com.crypto.funding.infrastructure.persistence.model.SignalCandidateEntity;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class SignalCandidateReviewService
{
    private final SignalCandidateJpaRepository candidateRepository;
    private final FundingEventCommandService fundingEventCommandService;
    private final TradeJournalService tradeJournalService;

    public SignalCandidateReviewService(
        SignalCandidateJpaRepository candidateRepository,
        FundingEventCommandService fundingEventCommandService,
        TradeJournalService tradeJournalService
    )
    {
        this.candidateRepository = candidateRepository;
        this.fundingEventCommandService = fundingEventCommandService;
        this.tradeJournalService = tradeJournalService;
    }

    @Transactional
    public SignalCandidate approve( ApproveSignalCandidateCommand command )
    {
        SignalCandidateEntity entity = loadCandidate( command.candidateId() );
        SignalCandidateStatus oldStatus = entity.getStatus();
        validateReviewable( entity );

        String symbol = resolveSymbol( entity, command.symbol() );
        String venue = resolveVenue( entity, command.venue() );

        Instant fundingTime = command.fundingTime() != null
                              ? command.fundingTime()
                              : entity.getSourceFundingTime();
        BigDecimal fundingRatePct = command.fundingRatePct() != null
                                    ? command.fundingRatePct()
                                    : entity.getSourceFundingRatePct();

        if( fundingTime == null )
        {
            throw new DomainValidationException(
                "Время funding нужно передать явно или получить из source snapshot для " + symbol + " на " + venue
            );
        }
        if( !fundingTime.isAfter( Instant.now() ) )
        {
            throw new DomainValidationException( "Нельзя подтвердить событие с funding в прошлом." );
        }

        FundingEvent fundingEvent = fundingEventCommandService.create(
            new CreateFundingEventCommand(
                venue,
                symbol,
                fundingTime,
                fundingRatePct,
                entity.getSourceType().toLowerCase( Locale.ROOT ),
                buildSourceRef( entity ),
                entity.getId()
            )
        );

        entity.setStatus( SignalCandidateStatus.EVENT_CREATED );
        entity.setReviewDecision( ReviewDecision.APPROVE );
        entity.setReviewedAt( Instant.now() );
        entity.setReviewNotes( normalizeNullable( command.reviewNotes() ) );
        entity.setFundingEventId( fundingEvent.id() );

        SignalCandidate saved = SignalCandidateMapper.toDomain( candidateRepository.save( entity ) );
        tradeJournalService.append(
            TradeJournalEntityType.SIGNAL_CANDIDATE,
            saved.id(),
            TradeJournalEventCode.CANDIDATE_APPROVED,
            oldStatus.name(),
            SignalCandidateStatus.EVENT_CREATED.name(),
            TradeJournalActorType.OPERATOR,
            "api",
            saved.reviewNotes()
        );
        return saved;
    }

    @Transactional
    public SignalCandidate reject( RejectSignalCandidateCommand command )
    {
        SignalCandidateEntity entity = loadCandidate( command.candidateId() );
        SignalCandidateStatus oldStatus = entity.getStatus();
        validateReviewable( entity );

        entity.setStatus( SignalCandidateStatus.REJECTED );
        entity.setReviewDecision( ReviewDecision.REJECT );
        entity.setReviewedAt( Instant.now() );
        entity.setReviewNotes( command.reviewNotes() != null ? command.reviewNotes().trim() : null );

        SignalCandidate saved = SignalCandidateMapper.toDomain( candidateRepository.save( entity ) );
        tradeJournalService.append(
            TradeJournalEntityType.SIGNAL_CANDIDATE,
            saved.id(),
            TradeJournalEventCode.CANDIDATE_REJECTED,
            oldStatus.name(),
            SignalCandidateStatus.REJECTED.name(),
            TradeJournalActorType.OPERATOR,
            "api",
            saved.reviewNotes()
        );
        return saved;
    }

    private SignalCandidateEntity loadCandidate( Long candidateId )
    {
        return candidateRepository.findById( candidateId )
                                  .orElseThrow( () -> new ResourceNotFoundException(
                                      "Сигнал не найден: " + candidateId
                                  ) );
    }

    private void validateReviewable( SignalCandidateEntity entity )
    {
        if( entity.getStatus() == SignalCandidateStatus.REJECTED
            || entity.getStatus() == SignalCandidateStatus.EVENT_CREATED
            || entity.getStatus() == SignalCandidateStatus.DELETED )
        {
            throw new DomainValidationException( "Кандидат " + entity.getId() + " уже находится в терминальном статусе " + entity.getStatus() );
        }
    }

    private String resolveSymbol( SignalCandidateEntity entity, String overrideSymbol )
    {
        if( overrideSymbol != null && !overrideSymbol.isBlank() )
        {
            return overrideSymbol.trim().toUpperCase( Locale.ROOT );
        }
        if( entity.getNormalizedSymbol() == null || entity.getNormalizedSymbol().isBlank() )
        {
            throw new DomainValidationException( "Нужно явно указать символ, потому что кандидат не нормализован." );
        }
        return entity.getNormalizedSymbol();
    }

    private String resolveVenue( SignalCandidateEntity entity, String overrideVenue )
    {
        if( overrideVenue != null && !overrideVenue.isBlank() )
        {
            return overrideVenue.trim().toLowerCase( Locale.ROOT );
        }
        List<String> venueHints = entity.getVenueHints();
        if( venueHints.size() != 1 )
        {
            throw new DomainValidationException( "Нужно явно указать площадку, потому что кандидат не резолвится в одну venue." );
        }
        return venueHints.getFirst();
    }

    private String buildSourceRef( SignalCandidateEntity entity )
    {
        if( entity.getSourceChatId() != null && entity.getSourceMessageId() != null )
        {
            return entity.getSourceType().toLowerCase( Locale.ROOT ) + ":" + entity.getSourceChatId() + ":" + entity.getSourceMessageId();
        }
        if( entity.getSourceChatId() != null )
        {
            return entity.getSourceType().toLowerCase( Locale.ROOT ) + ":" + entity.getSourceChatId();
        }
        return entity.getSourceType().toLowerCase( Locale.ROOT ) + ":candidate:" + entity.getId();
    }

    private static String normalizeNullable( String value )
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
