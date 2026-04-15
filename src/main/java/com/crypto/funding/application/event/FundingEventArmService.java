package com.crypto.funding.application.event;

import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.trade.ArmedTradeCommandService;
import com.crypto.funding.application.trade.CreateArmedTradeCommand;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.TradeArmSource;
import com.crypto.funding.domain.trade.TradeJournalActorType;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FundingEventArmService
{
    private final FundingEventJpaRepository fundingEventRepository;
    private final ArmedTradeCommandService armedTradeCommandService;
    private final FundingEventLifecycleService fundingEventLifecycleService;

    public FundingEventArmService(
        FundingEventJpaRepository fundingEventRepository,
        ArmedTradeCommandService armedTradeCommandService,
        FundingEventLifecycleService fundingEventLifecycleService
    )
    {
        this.fundingEventRepository = fundingEventRepository;
        this.armedTradeCommandService = armedTradeCommandService;
        this.fundingEventLifecycleService = fundingEventLifecycleService;
    }

    @Transactional
    public ArmedTrade arm( Long fundingEventId, ArmFundingEventCommand command )
    {
        fundingEventLifecycleService.expirePastEvents();
        FundingEventEntity fundingEvent = fundingEventRepository.findById( fundingEventId )
                                                               .orElseThrow( () -> new ResourceNotFoundException(
                                                                   "Событие фандинга не найдено: " + fundingEventId
                                                               ) );

        if( fundingEvent.getStatus() == FundingEventStatus.CANCELLED || fundingEvent.getStatus() == FundingEventStatus.EXPIRED )
        {
            throw new DomainValidationException( "Событие " + fundingEventId + " нельзя подготовить из статуса " + fundingEvent.getStatus() );
        }

        ArmedTrade armedTrade = armedTradeCommandService.create(
            new CreateArmedTradeCommand(
                fundingEventId,
                command.notionalUsd(),
                command.intendedSide(),
                command.plannedEntryAt(),
                command.plannedExitAt(),
                command.entryAttemptCount(),
                command.entrySpacingMs(),
                command.manualLatencyAdjustmentMs(),
                command.notes()
            ),
            TradeArmSource.EVENT_API,
            TradeJournalActorType.OPERATOR,
            "api"
        );
        return armedTrade;
    }
}
