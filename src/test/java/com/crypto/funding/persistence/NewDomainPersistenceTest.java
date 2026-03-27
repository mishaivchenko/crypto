package com.crypto.funding.persistence;

import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.domain.execution.ExecutionType;
import com.crypto.funding.domain.execution.OrderAttemptStatus;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.PositionState;
import com.crypto.funding.domain.trade.TradeSide;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.model.OrderAttemptEntity;
import com.crypto.funding.infrastructure.persistence.model.PositionEntity;
import com.crypto.funding.infrastructure.persistence.model.TradeOutcomeEntity;
import com.crypto.funding.infrastructure.persistence.model.VenueTimingProfileEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.OrderAttemptJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.PositionJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.TradeOutcomeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.VenueTimingProfileJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.datasource.url=jdbc:sqlite:./build/test-new-domain.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect"
})
class NewDomainPersistenceTest
{
    @Autowired
    private FundingEventJpaRepository fundingEventRepository;

    @Autowired
    private ArmedTradeJpaRepository armedTradeRepository;

    @Autowired
    private OrderAttemptJpaRepository orderAttemptRepository;

    @Autowired
    private PositionJpaRepository positionRepository;

    @Autowired
    private TradeOutcomeJpaRepository tradeOutcomeRepository;

    @Autowired
    private VenueTimingProfileJpaRepository venueTimingProfileRepository;

    @Test
    void storesNewDomainEntities()
    {
        FundingEventEntity fundingEvent = new FundingEventEntity();
        fundingEvent.setVenue( "gate" );
        fundingEvent.setSymbol( "BTC/USDT" );
        fundingEvent.setFundingTime( Instant.now().plusSeconds( 3600 ) );
        fundingEvent.setFundingRatePct( new BigDecimal( "0.0150" ) );
        fundingEvent.setStatus( FundingEventStatus.DISCOVERED );
        fundingEvent.setSourceType( "telegram" );
        fundingEvent.setSourceRef( "@funding_watchdog" );
        fundingEvent.setDiscoveredAt( Instant.now() );
        FundingEventEntity savedEvent = fundingEventRepository.save( fundingEvent );

        ArmedTradeEntity armedTrade = new ArmedTradeEntity();
        armedTrade.setFundingEventId( savedEvent.getId() );
        armedTrade.setNotionalUsd( new BigDecimal( "25" ) );
        armedTrade.setIntendedSide( TradeSide.LONG );
        armedTrade.setPlannedEntryAt( Instant.now().plusSeconds( 300 ) );
        armedTrade.setPlannedExitAt( Instant.now().plusSeconds( 900 ) );
        armedTrade.setState( ArmedTradeState.ARMED );
        ArmedTradeEntity savedTrade = armedTradeRepository.save( armedTrade );

        OrderAttemptEntity orderAttempt = new OrderAttemptEntity();
        orderAttempt.setArmedTradeId( savedTrade.getId() );
        orderAttempt.setVenue( "gate" );
        orderAttempt.setSymbol( "BTC/USDT" );
        orderAttempt.setSide( TradeSide.LONG );
        orderAttempt.setExecutionType( ExecutionType.MARKET );
        orderAttempt.setQuantity( new BigDecimal( "0.001" ) );
        orderAttempt.setStatus( OrderAttemptStatus.CREATED );

        PositionEntity position = new PositionEntity();
        position.setArmedTradeId( savedTrade.getId() );
        position.setVenue( "gate" );
        position.setSymbol( "BTC/USDT" );
        position.setSide( TradeSide.LONG );
        position.setQuantity( new BigDecimal( "0.001" ) );
        position.setState( PositionState.PENDING_OPEN );

        TradeOutcomeEntity outcome = new TradeOutcomeEntity();
        outcome.setArmedTradeId( savedTrade.getId() );
        outcome.setOutcomeCode( "PENDING_REVIEW" );
        outcome.setEvaluatedAt( Instant.now() );

        VenueTimingProfileEntity profile = new VenueTimingProfileEntity();
        profile.setVenue( "gate" );
        profile.setSymbol( "BTC/USDT" );
        profile.setObservedLagMs( 120L );
        profile.setSampledAt( Instant.now() );

        assertThat( orderAttemptRepository.save( orderAttempt ).getId() ).isNotNull();
        assertThat( positionRepository.save( position ).getId() ).isNotNull();
        assertThat( tradeOutcomeRepository.save( outcome ).getId() ).isNotNull();
        assertThat( venueTimingProfileRepository.save( profile ).getId() ).isNotNull();

        assertThat( fundingEventRepository.findAll() ).hasSize( 1 );
        assertThat( armedTradeRepository.findAll() ).hasSize( 1 );
        assertThat( orderAttemptRepository.findAll() ).hasSize( 1 );
        assertThat( positionRepository.findAll() ).hasSize( 1 );
        assertThat( tradeOutcomeRepository.findAll() ).hasSize( 1 );
        assertThat( venueTimingProfileRepository.findAll() ).hasSize( 1 );
    }
}
