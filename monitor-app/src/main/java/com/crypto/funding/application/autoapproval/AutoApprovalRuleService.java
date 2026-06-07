package com.crypto.funding.application.autoapproval;

import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.domain.autoapproval.AutoApprovalRule;
import com.crypto.funding.infrastructure.persistence.mapper.AutoApprovalRuleMapper;
import com.crypto.funding.infrastructure.persistence.model.AutoApprovalRuleEntity;
import com.crypto.funding.infrastructure.persistence.repository.AutoApprovalRuleJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AutoApprovalRuleService
{
    private final AutoApprovalRuleJpaRepository repository;

    public AutoApprovalRuleService( AutoApprovalRuleJpaRepository repository )
    {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AutoApprovalRule> list()
    {
        return repository.findAllByOrderByPriorityAsc()
                         .stream()
                         .map( AutoApprovalRuleMapper::toDomain )
                         .toList();
    }

    @Transactional(readOnly = true)
    public List<AutoApprovalRule> listActive()
    {
        return repository.findAllByEnabledTrueOrderByPriorityAsc()
                         .stream()
                         .map( AutoApprovalRuleMapper::toDomain )
                         .toList();
    }

    @Transactional
    public AutoApprovalRule create( CreateAutoApprovalRuleCommand command )
    {
        if( command.name() == null || command.name().isBlank() )
        {
            throw new DomainValidationException( "Имя правила не может быть пустым" );
        }
        if( command.defaultNotionalUsd() == null || command.defaultNotionalUsd().signum() <= 0 )
        {
            throw new DomainValidationException( "defaultNotionalUsd должен быть положительным" );
        }
        if( command.defaultSide() == null )
        {
            throw new DomainValidationException( "defaultSide обязателен" );
        }
        if( command.action() == null )
        {
            throw new DomainValidationException( "action обязателен" );
        }

        AutoApprovalRuleEntity entity = new AutoApprovalRuleEntity();
        applyCommand( entity, command );
        return AutoApprovalRuleMapper.toDomain( repository.save( entity ) );
    }

    @Transactional
    public AutoApprovalRule update( Long id, UpdateAutoApprovalRuleCommand command )
    {
        AutoApprovalRuleEntity entity = load( id );
        if( command.name() != null )
        {
            entity.setName( command.name().trim() );
        }
        if( command.enabled() != null )
        {
            entity.setEnabled( command.enabled() );
        }
        if( command.mode() != null )
        {
            entity.setMode( command.mode() );
        }
        if( command.minFundingRatePct() != null || entity.getMinFundingRatePct() != null )
        {
            entity.setMinFundingRatePct( command.minFundingRatePct() );
        }
        if( command.maxFundingRatePct() != null || entity.getMaxFundingRatePct() != null )
        {
            entity.setMaxFundingRatePct( command.maxFundingRatePct() );
        }
        if( command.allowedVenues() != null )
        {
            entity.setAllowedVenues( AutoApprovalRuleMapper.serializeList( command.allowedVenues() ) );
        }
        if( command.allowedAiRecommendations() != null )
        {
            entity.setAllowedAiRecommendations( AutoApprovalRuleMapper.serializeList( command.allowedAiRecommendations() ) );
        }
        if( command.minAiConfidence() != null || entity.getMinAiConfidence() != null )
        {
            entity.setMinAiConfidence( command.minAiConfidence() );
        }
        if( command.allowedLiquidityScores() != null )
        {
            entity.setAllowedLiquidityScores( AutoApprovalRuleMapper.serializeList( command.allowedLiquidityScores() ) );
        }
        if( command.defaultNotionalUsd() != null )
        {
            entity.setDefaultNotionalUsd( command.defaultNotionalUsd() );
        }
        if( command.defaultSide() != null )
        {
            entity.setDefaultSide( command.defaultSide() );
        }
        if( command.action() != null )
        {
            entity.setAction( command.action() );
        }
        if( command.priority() != null )
        {
            entity.setPriority( command.priority() );
        }
        if( command.notes() != null )
        {
            entity.setNotes( command.notes().isBlank() ? null : command.notes().trim() );
        }
        return AutoApprovalRuleMapper.toDomain( repository.save( entity ) );
    }

    @Transactional
    public void delete( Long id )
    {
        load( id );
        repository.deleteById( id );
    }

    @Transactional
    public AutoApprovalRule enable( Long id )
    {
        AutoApprovalRuleEntity entity = load( id );
        entity.setEnabled( true );
        return AutoApprovalRuleMapper.toDomain( repository.save( entity ) );
    }

    @Transactional
    public AutoApprovalRule disable( Long id )
    {
        AutoApprovalRuleEntity entity = load( id );
        entity.setEnabled( false );
        return AutoApprovalRuleMapper.toDomain( repository.save( entity ) );
    }

    private void applyCommand( AutoApprovalRuleEntity entity, CreateAutoApprovalRuleCommand command )
    {
        entity.setName( command.name().trim() );
        entity.setEnabled( command.enabled() );
        entity.setMode( command.mode() != null ? command.mode() : "BOTH" );
        entity.setMinFundingRatePct( command.minFundingRatePct() );
        entity.setMaxFundingRatePct( command.maxFundingRatePct() );
        entity.setAllowedVenues( AutoApprovalRuleMapper.serializeList( command.allowedVenues() ) );
        entity.setAllowedAiRecommendations( AutoApprovalRuleMapper.serializeList( command.allowedAiRecommendations() ) );
        entity.setMinAiConfidence( command.minAiConfidence() );
        entity.setAllowedLiquidityScores( AutoApprovalRuleMapper.serializeList( command.allowedLiquidityScores() ) );
        entity.setDefaultNotionalUsd( command.defaultNotionalUsd() );
        entity.setDefaultSide( command.defaultSide() );
        entity.setAction( command.action() );
        entity.setPriority( command.priority() );
        entity.setNotes( command.notes() );
    }

    private AutoApprovalRuleEntity load( Long id )
    {
        return repository.findById( id )
                         .orElseThrow( () -> new ResourceNotFoundException( "Правило авто-апрува не найдено: " + id ) );
    }
}
