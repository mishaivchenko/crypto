package com.crypto.funding.api;

import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeArmSource;
import com.crypto.funding.domain.trade.TradeSide;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.OrderAttemptJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.PositionJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.TradeOutcomeJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:sqlite:./build/test-internal-engine-plan-api.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "trading.candidate-source.enabled=false",
    "trading.metadata.sync-on-startup=false",
    "trading.metadata.schedule-enabled=false",
    "trading.metadata.require-credentials-on-startup=false",
    "security.operators.auth-enabled=false",
    "security.operators.internal-token=test-internal-token"
})
@AutoConfigureMockMvc
class InternalEnginePlanApiIntegrationTest
{
    @Autowired
    private MockMvc mockMvc;

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

    @BeforeEach
    void clean()
    {
        tradeOutcomeRepository.deleteAll();
        positionRepository.deleteAll();
        orderAttemptRepository.deleteAll();
        armedTradeRepository.deleteAll();
        fundingEventRepository.deleteAll();
    }

    @Test
    void protectsEnginePlanApiWithInternalToken() throws Exception
    {
        // REQ: ENG-ACC-006
        mockMvc.perform( get( "/internal/v1/engine/plans" ) )
               .andExpect( status().isUnauthorized() )
               .andExpect( jsonPath( "$.message" ).value( "Valid X-Internal-Token is required." ) );

        mockMvc.perform( get( "/internal/v1/engine/plans" )
                .header( "X-Internal-Token", "test-internal-token" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$" ).isArray() );
    }

    @Test
    void recordsOrderAttemptsIdempotently() throws Exception
    {
        // REQ: ENG-ACC-006
        ArmedTradeEntity trade = createArmedTrade();
        String payload = """
            {
              "attemptKey":"entry:%d:1:2030-01-01T00:00:00Z",
              "armedTradeId":%d,
              "attemptNumber":1,
              "venue":"bybit",
              "symbol":"REQ/USDT",
              "side":"SHORT",
              "executionType":"MARKET",
              "quantity":25,
              "status":"FAILED",
              "targetEntryAt":"2030-01-01T00:00:00Z",
              "triggerAt":"2029-12-31T23:59:59Z",
              "submittedAt":"2029-12-31T23:59:59Z",
              "failureReason":"Missing engine credentials"
            }
            """.formatted( trade.getId(), trade.getId() );

        mockMvc.perform( post( "/internal/v1/engine/order-attempts" )
                .header( "X-Internal-Token", "test-internal-token" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( payload ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.armedTradeId" ).value( trade.getId() ) )
            .andExpect( jsonPath( "$.attemptNumber" ).value( 1 ) )
            .andExpect( jsonPath( "$.status" ).value( "FAILED" ) )
            .andExpect( jsonPath( "$.failureReason" ).value( "Missing engine credentials" ) );

        mockMvc.perform( post( "/internal/v1/engine/order-attempts" )
                .header( "X-Internal-Token", "test-internal-token" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( payload ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.armedTradeId" ).value( trade.getId() ) );

        org.assertj.core.api.Assertions.assertThat( orderAttemptRepository.findAll() ).hasSize( 1 );

        mockMvc.perform( get( "/api/v1/armed-trades/{id}/order-attempts", trade.getId() ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].attemptKey" ).value( "entry:" + trade.getId() + ":1:2030-01-01T00:00:00Z" ) );
    }

    @Test
    void recordsPositionStateAndOutcomeForEngineLifecycle() throws Exception
    {
        ArmedTradeEntity trade = createArmedTrade();

        mockMvc.perform( post( "/internal/v1/engine/trades/{id}/state", trade.getId() )
                .header( "X-Internal-Token", "test-internal-token" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( "{\"state\":\"OPEN\",\"note\":\"entry filled\"}" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.state" ).value( "OPEN" ) );

        mockMvc.perform( post( "/internal/v1/engine/positions" )
                .header( "X-Internal-Token", "test-internal-token" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "armedTradeId":%d,
                      "venue":"bybit",
                      "symbol":"REQ/USDT",
                      "side":"SHORT",
                      "quantity":10,
                      "entryPrice":2.5,
                      "state":"OPEN",
                      "openedAt":"2030-01-01T00:00:00Z"
                    }
                    """.formatted( trade.getId() ) ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.armedTradeId" ).value( trade.getId() ) )
            .andExpect( jsonPath( "$.state" ).value( "OPEN" ) )
            .andExpect( jsonPath( "$.entryPrice" ).value( 2.5 ) );

        mockMvc.perform( post( "/internal/v1/engine/trades/{id}/state", trade.getId() )
                .header( "X-Internal-Token", "test-internal-token" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( "{\"state\":\"CLOSED\",\"note\":\"exit filled\"}" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.state" ).value( "CLOSED" ) );

        mockMvc.perform( post( "/internal/v1/engine/outcomes" )
                .header( "X-Internal-Token", "test-internal-token" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "armedTradeId":%d,
                      "grossPnlUsd":1.2,
                      "netPnlUsd":1.18,
                      "feesUsd":0.02,
                      "outcomeCode":"CLOSED",
                      "notes":"entry/exit filled",
                      "evaluatedAt":"2030-01-01T00:01:00Z"
                    }
                    """.formatted( trade.getId() ) ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.armedTradeId" ).value( trade.getId() ) )
            .andExpect( jsonPath( "$.outcomeCode" ).value( "CLOSED" ) );

        org.assertj.core.api.Assertions.assertThat( armedTradeRepository.findById( trade.getId() ).orElseThrow().getState() )
                                       .isEqualTo( ArmedTradeState.CLOSED );
        org.assertj.core.api.Assertions.assertThat( positionRepository.findAll() ).hasSize( 1 );
        org.assertj.core.api.Assertions.assertThat( tradeOutcomeRepository.findAll() ).hasSize( 1 );
    }

    @Test
    void includeAllPlansStillReturnsOverdueArmedTradesAfterFundingEventExpires() throws Exception
    {
        // REQ: ENG-ACC-006
        ArmedTradeEntity trade = createStaleArmedTrade();

        mockMvc.perform( get( "/internal/v1/engine/plans" )
                .header( "X-Internal-Token", "test-internal-token" )
                .param( "includeAll", "true" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].armedTradeId" ).value( trade.getId() ) )
            .andExpect( jsonPath( "$[0].status" ).value( "OVERDUE" ) );
    }

    private ArmedTradeEntity createArmedTrade()
    {
        FundingEventEntity event = new FundingEventEntity();
        event.setVenue( "bybit" );
        event.setSymbol( "REQ/USDT" );
        event.setFundingTime( Instant.parse( "2030-01-01T00:00:00Z" ) );
        event.setFundingRatePct( BigDecimal.valueOf( -0.01 ) );
        event.setStatus( FundingEventStatus.ARMED );
        event.setSourceType( "FUNDING_API" );
        event.setSourceRef( "test" );
        event.setDiscoveredAt( Instant.parse( "2029-12-31T23:00:00Z" ) );
        FundingEventEntity savedEvent = fundingEventRepository.save( event );

        ArmedTradeEntity trade = new ArmedTradeEntity();
        trade.setFundingEventId( savedEvent.getId() );
        trade.setNotionalUsd( BigDecimal.valueOf( 25 ) );
        trade.setIntendedSide( TradeSide.SHORT );
        trade.setPlannedEntryAt( Instant.parse( "2029-12-31T23:59:00Z" ) );
        trade.setPlannedExitAt( Instant.parse( "2030-01-01T00:01:00Z" ) );
        trade.setArmedAt( Instant.parse( "2029-12-31T23:30:00Z" ) );
        trade.setEntryAttemptCount( 3 );
        trade.setEntrySpacingMs( 150L );
        trade.setEffectiveEntryLatencyMs( 0L );
        trade.setArmSource( TradeArmSource.EVENT_API );
        trade.setState( ArmedTradeState.ARMED );
        return armedTradeRepository.save( trade );
    }

    private ArmedTradeEntity createStaleArmedTrade()
    {
        FundingEventEntity event = new FundingEventEntity();
        event.setVenue( "kucoin" );
        event.setSymbol( "VOOI/USDT" );
        event.setFundingTime( Instant.parse( "2020-01-01T00:00:00Z" ) );
        event.setFundingRatePct( BigDecimal.valueOf( -0.003916 ) );
        event.setStatus( FundingEventStatus.ARMED );
        event.setSourceType( "FUNDING_API" );
        event.setSourceRef( "stale-vooi" );
        event.setDiscoveredAt( Instant.parse( "2019-12-31T23:00:00Z" ) );
        FundingEventEntity savedEvent = fundingEventRepository.save( event );

        ArmedTradeEntity trade = new ArmedTradeEntity();
        trade.setFundingEventId( savedEvent.getId() );
        trade.setNotionalUsd( BigDecimal.valueOf( 25 ) );
        trade.setIntendedSide( TradeSide.SHORT );
        trade.setPlannedEntryAt( Instant.parse( "2019-12-31T23:59:00Z" ) );
        trade.setPlannedExitAt( Instant.parse( "2020-01-01T00:01:00Z" ) );
        trade.setArmedAt( Instant.parse( "2019-12-31T23:30:00Z" ) );
        trade.setEntryAttemptCount( 3 );
        trade.setEntrySpacingMs( 150L );
        trade.setEffectiveEntryLatencyMs( 0L );
        trade.setArmSource( TradeArmSource.EVENT_API );
        trade.setState( ArmedTradeState.ARMED );
        return armedTradeRepository.save( trade );
    }
}
