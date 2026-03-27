package com.crypto.funding.application.trade;

import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.infrastructure.persistence.mapper.ArmedTradeMapper;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArmedTradeCommandService
{
    private final FundingEventJpaRepository fundingEventRepository;
    private final ArmedTradeJpaRepository armedTradeRepository;

    public ArmedTradeCommandService(
        FundingEventJpaRepository fundingEventRepository,
        ArmedTradeJpaRepository armedTradeRepository
    )
    {
        this.fundingEventRepository = fundingEventRepository;
        this.armedTradeRepository = armedTradeRepository;
    }

    @Transactional
    public ArmedTrade create( CreateArmedTradeCommand command )
    {
        FundingEventEntity fundingEvent = fundingEventRepository.findById( command.fundingEventId() )
                                                               .orElseThrow( () -> new ResourceNotFoundException(
                                                                   "FundingEvent not found: " + command.fundingEventId()
                                                               ) );

        if( fundingEvent.getStatus() == FundingEventStatus.CANCELLED || fundingEvent.getStatus() == FundingEventStatus.EXPIRED )
        {
            throw new DomainValidationException(
                "FundingEvent " + command.fundingEventId() + " is not armable from status " + fundingEvent.getStatus()
            );
        }

        ArmedTradeEntity entity = new ArmedTradeEntity();
        entity.setFundingEventId( fundingEvent.getId() );
        entity.setNotionalUsd( command.notionalUsd() );
        entity.setIntendedSide( command.intendedSide() );
        entity.setPlannedEntryAt( command.plannedEntryAt() );
        entity.setPlannedExitAt( command.plannedExitAt() );
        entity.setState( ArmedTradeState.ARMED );
        entity.setNotes( command.notes() == null || command.notes().isBlank() ? null : command.notes().trim() );

        ArmedTradeEntity saved = armedTradeRepository.save( entity );
        fundingEvent.setStatus( FundingEventStatus.ARMED );
        fundingEventRepository.save( fundingEvent );
        return ArmedTradeMapper.toDomain( saved );
    }
}
