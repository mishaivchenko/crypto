package com.crypto.funding.application.autoapproval;

import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.domain.autoapproval.AutoApprovalAction;
import com.crypto.funding.domain.autoapproval.AutoApprovalRule;
import com.crypto.funding.domain.trade.TradeSide;
import com.crypto.funding.infrastructure.persistence.model.AutoApprovalRuleEntity;
import com.crypto.funding.infrastructure.persistence.repository.AutoApprovalRuleJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoApprovalRuleServiceTest
{
    private AutoApprovalRuleJpaRepository repository;
    private AutoApprovalRuleService service;

    @BeforeEach
    void setUp()
    {
        repository = mock( AutoApprovalRuleJpaRepository.class );
        service = new AutoApprovalRuleService( repository );
    }

    @Test
    void createSavesEntityAndReturnsDomain()
    {
        AutoApprovalRuleEntity saved = entityWithId( 1L );
        when( repository.save( any() ) ).thenReturn( saved );

        AutoApprovalRule result = service.create( validCreateCommand() );

        assertThat( result.id() ).isEqualTo( 1L );
        assertThat( result.name() ).isEqualTo( "test-rule" );
        assertThat( result.action() ).isEqualTo( AutoApprovalAction.AUTO_EXECUTE );
        verify( repository ).save( any( AutoApprovalRuleEntity.class ) );
    }

    @Test
    void createThrowsWhenNameBlank()
    {
        CreateAutoApprovalRuleCommand cmd = new CreateAutoApprovalRuleCommand(
            "  ", true, "BOTH", null, null, null, null, null, null,
            new BigDecimal( "100" ), TradeSide.SHORT, AutoApprovalAction.AUTO_EXECUTE, 1, null
        );
        assertThatThrownBy( () -> service.create( cmd ) )
            .isInstanceOf( DomainValidationException.class );
    }

    @Test
    void createThrowsWhenNotionalZero()
    {
        CreateAutoApprovalRuleCommand cmd = new CreateAutoApprovalRuleCommand(
            "rule", true, "BOTH", null, null, null, null, null, null,
            BigDecimal.ZERO, TradeSide.SHORT, AutoApprovalAction.AUTO_EXECUTE, 1, null
        );
        assertThatThrownBy( () -> service.create( cmd ) )
            .isInstanceOf( DomainValidationException.class );
    }

    @Test
    void createThrowsWhenNotionalNull()
    {
        CreateAutoApprovalRuleCommand cmd = new CreateAutoApprovalRuleCommand(
            "rule", true, "BOTH", null, null, null, null, null, null,
            null, TradeSide.SHORT, AutoApprovalAction.AUTO_EXECUTE, 1, null
        );
        assertThatThrownBy( () -> service.create( cmd ) )
            .isInstanceOf( DomainValidationException.class );
    }

    @Test
    void createThrowsWhenSideNull()
    {
        CreateAutoApprovalRuleCommand cmd = new CreateAutoApprovalRuleCommand(
            "rule", true, "BOTH", null, null, null, null, null, null,
            new BigDecimal( "100" ), null, AutoApprovalAction.AUTO_EXECUTE, 1, null
        );
        assertThatThrownBy( () -> service.create( cmd ) )
            .isInstanceOf( DomainValidationException.class );
    }

    @Test
    void updateThrowsWhenRuleNotFound()
    {
        when( repository.findById( 99L ) ).thenReturn( Optional.empty() );
        assertThatThrownBy( () -> service.update( 99L, new UpdateAutoApprovalRuleCommand(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null
        ) ) ).isInstanceOf( ResourceNotFoundException.class );
    }

    @Test
    void updatePartiallyChangesOnlyProvidedFields()
    {
        AutoApprovalRuleEntity entity = entityWithId( 1L );
        entity.setName( "original-name" );
        entity.setPriority( 50 );
        when( repository.findById( 1L ) ).thenReturn( Optional.of( entity ) );
        when( repository.save( any() ) ).thenAnswer( inv -> inv.getArgument( 0 ) );

        UpdateAutoApprovalRuleCommand cmd = new UpdateAutoApprovalRuleCommand(
            "new-name", null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        AutoApprovalRule result = service.update( 1L, cmd );

        assertThat( result.name() ).isEqualTo( "new-name" );
        assertThat( result.priority() ).isEqualTo( 50 );
    }

    @Test
    void deleteRemovesByIdAfterLoad()
    {
        AutoApprovalRuleEntity entity = entityWithId( 1L );
        when( repository.findById( 1L ) ).thenReturn( Optional.of( entity ) );

        service.delete( 1L );

        verify( repository ).deleteById( 1L );
    }

    @Test
    void deleteThrowsWhenNotFound()
    {
        when( repository.findById( 99L ) ).thenReturn( Optional.empty() );
        assertThatThrownBy( () -> service.delete( 99L ) )
            .isInstanceOf( ResourceNotFoundException.class );
    }

    @Test
    void enableSetsEnabledTrue()
    {
        AutoApprovalRuleEntity entity = entityWithId( 1L );
        entity.setEnabled( false );
        when( repository.findById( 1L ) ).thenReturn( Optional.of( entity ) );
        when( repository.save( any() ) ).thenAnswer( inv -> inv.getArgument( 0 ) );

        AutoApprovalRule result = service.enable( 1L );
        assertThat( result.enabled() ).isTrue();
    }

    @Test
    void disableSetsEnabledFalse()
    {
        AutoApprovalRuleEntity entity = entityWithId( 1L );
        entity.setEnabled( true );
        when( repository.findById( 1L ) ).thenReturn( Optional.of( entity ) );
        when( repository.save( any() ) ).thenAnswer( inv -> inv.getArgument( 0 ) );

        AutoApprovalRule result = service.disable( 1L );
        assertThat( result.enabled() ).isFalse();
    }

    @Test
    void listActiveUsesEnabledRepository()
    {
        AutoApprovalRuleEntity e1 = entityWithId( 1L );
        when( repository.findAllByEnabledTrueOrderByPriorityAsc() ).thenReturn( List.of( e1 ) );

        List<AutoApprovalRule> result = service.listActive();
        assertThat( result ).hasSize( 1 );
        verify( repository ).findAllByEnabledTrueOrderByPriorityAsc();
    }

    // --- helpers ---

    private static CreateAutoApprovalRuleCommand validCreateCommand()
    {
        return new CreateAutoApprovalRuleCommand(
            "test-rule", true, "BOTH",
            null, null, null, null, null, null,
            new BigDecimal( "100" ), TradeSide.SHORT, AutoApprovalAction.AUTO_EXECUTE, 1, null
        );
    }

    private static AutoApprovalRuleEntity entityWithId( Long id )
    {
        AutoApprovalRuleEntity e = new AutoApprovalRuleEntity();
        org.springframework.test.util.ReflectionTestUtils.setField( e, "id", id );
        e.setName( "test-rule" );
        e.setEnabled( true );
        e.setMode( "BOTH" );
        e.setDefaultNotionalUsd( new BigDecimal( "100" ) );
        e.setDefaultSide( TradeSide.SHORT );
        e.setAction( AutoApprovalAction.AUTO_EXECUTE );
        e.setPriority( 1 );
        return e;
    }
}
