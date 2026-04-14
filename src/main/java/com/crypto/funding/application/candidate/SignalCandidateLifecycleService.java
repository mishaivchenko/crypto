package com.crypto.funding.application.candidate;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.trade.TradeJournalService;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.domain.trade.TradeJournalActorType;
import com.crypto.funding.domain.trade.TradeJournalEntityType;
import com.crypto.funding.domain.trade.TradeJournalEventCode;
import com.crypto.funding.infrastructure.persistence.mapper.SignalCandidateMapper;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.model.SignalCandidateEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.TradeJournalEntryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SignalCandidateLifecycleService
{
    private final SignalCandidateJpaRepository signalCandidateRepository;
    private final FundingEventJpaRepository fundingEventRepository;
    private final ArmedTradeJpaRepository armedTradeRepository;
    private final TradeJournalEntryJpaRepository tradeJournalEntryRepository;
    private final TradeJournalService tradeJournalService;

    public SignalCandidateLifecycleService(
        SignalCandidateJpaRepository signalCandidateRepository,
        FundingEventJpaRepository fundingEventRepository,
        ArmedTradeJpaRepository armedTradeRepository,
        TradeJournalEntryJpaRepository tradeJournalEntryRepository,
        TradeJournalService tradeJournalService
    )
    {
        this.signalCandidateRepository = signalCandidateRepository;
        this.fundingEventRepository = fundingEventRepository;
        this.armedTradeRepository = armedTradeRepository;
        this.tradeJournalEntryRepository = tradeJournalEntryRepository;
        this.tradeJournalService = tradeJournalService;
    }

    @Transactional
    public SignalCandidate deleteCandidate( Long candidateId, String note )
    {
        SignalCandidateEntity candidate = signalCandidateRepository.findById( candidateId )
                                                                   .orElseThrow( () -> new ResourceNotFoundException(
                                                                       "Сигнал не найден: " + candidateId
                                                                   ) );

        removeLinkedPipeline( candidate );

        SignalCandidateStatus oldStatus = candidate.getStatus();
        candidate.setStatus( SignalCandidateStatus.DELETED );
        candidate.setReviewedAt( Instant.now() );
        candidate.setFundingEventId( null );
        candidate.setReviewNotes( normalizeNote( note, candidate.getReviewNotes() ) );

        SignalCandidateEntity saved = signalCandidateRepository.save( candidate );
        tradeJournalService.append(
            TradeJournalEntityType.SIGNAL_CANDIDATE,
            saved.getId(),
            TradeJournalEventCode.CANDIDATE_DELETED,
            oldStatus.name(),
            SignalCandidateStatus.DELETED.name(),
            TradeJournalActorType.OPERATOR,
            "api",
            saved.getReviewNotes()
        );
        return SignalCandidateMapper.toDomain( saved );
    }

    private void removeLinkedPipeline( SignalCandidateEntity candidate )
    {
        FundingEventEntity fundingEvent = fundingEventRepository.findBySignalCandidateId( candidate.getId() ).orElse( null );
        if( fundingEvent == null && candidate.getFundingEventId() != null )
        {
            fundingEvent = fundingEventRepository.findById( candidate.getFundingEventId() ).orElse( null );
        }
        if( fundingEvent == null )
        {
            return;
        }

        for( ArmedTradeEntity armedTrade : armedTradeRepository.findAllByFundingEventIdOrderByCreatedAtDesc( fundingEvent.getId() ) )
        {
            tradeJournalEntryRepository.deleteAllByEntityTypeAndEntityId( TradeJournalEntityType.ARMED_TRADE, armedTrade.getId() );
            armedTradeRepository.delete( armedTrade );
        }

        tradeJournalEntryRepository.deleteAllByEntityTypeAndEntityId( TradeJournalEntityType.FUNDING_EVENT, fundingEvent.getId() );
        fundingEventRepository.delete( fundingEvent );
    }

    private String normalizeNote( String requestedNote, String existingNote )
    {
        String normalizedRequested = requestedNote == null || requestedNote.isBlank() ? null : requestedNote.trim();
        if( normalizedRequested != null )
        {
            return normalizedRequested;
        }
        if( existingNote != null && !existingNote.isBlank() )
        {
            return existingNote.trim();
        }
        return "Удалено оператором.";
    }
}
