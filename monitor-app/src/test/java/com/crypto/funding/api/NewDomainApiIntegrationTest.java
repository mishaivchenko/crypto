package com.crypto.funding.api;

import com.crypto.funding.application.candidate.IngestSignalCandidateCommand;
import com.crypto.funding.application.candidate.SignalCandidateIngestService;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeArmSource;
import com.crypto.funding.domain.trade.TradeSide;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import com.crypto.funding.support.ManualSignalTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:sqlite:./build/test-new-domain-api.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "security.operators.auth-enabled=false"
})
@AutoConfigureMockMvc
class NewDomainApiIntegrationTest
{
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FundingEventJpaRepository fundingEventRepository;

    @Autowired
    private ArmedTradeJpaRepository armedTradeRepository;

    @Autowired
    private SignalCandidateJpaRepository signalCandidateRepository;

    @Autowired
    private SignalCandidateIngestService signalCandidateIngestService;

    @Autowired
    private VenueRequestTimingService venueRequestTimingService;

    @BeforeEach
    void clean()
    {
        armedTradeRepository.deleteAll();
        fundingEventRepository.deleteAll();
        signalCandidateRepository.deleteAll();
        venueRequestTimingService.clear();
    }

    @Test
    void createsFundingEventAndArmedTrade() throws Exception
    {
        String fundingEventBody = """
            {
              "venue":"gate",
              "symbol":"BTC/USDT",
              "fundingTime":"2030-01-01T00:00:00Z",
              "fundingRatePct":0.015,
              "sourceType":"telegram",
              "sourceRef":"@funding_watchdog"
            }
            """;

        String response = mockMvc.perform( post( "/api/v1/funding-events" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( fundingEventBody ) )
            .andExpect( status().isCreated() )
            .andExpect( jsonPath( "$.status" ).value( "DISCOVERED" ) )
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long fundingEventId = fundingEventRepository.findAll().getFirst().getId();
        assertThat( response ).contains( "\"id\":" + fundingEventId );

        mockMvc.perform( get( "/api/v1/funding-events" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.content[0].id" ).value( fundingEventId ) )
            .andExpect( jsonPath( "$.content[0].status" ).value( "DISCOVERED" ) );

        String armFundingEventBody = """
            {
              "notionalUsd": 25,
              "intendedSide": "SHORT",
              "plannedEntryAt": "2029-12-31T23:59:50Z",
              "plannedExitAt": "2030-01-01T00:01:00Z",
              "entryAttemptCount": 4,
              "entrySpacingMs": 125,
              "manualLatencyAdjustmentMs": 15,
              "notes": "manual arm"
            }
            """;

        venueRequestTimingService.recordSuccess( "gate", "credential-check", 50_000_000L, 0L, 200 );

        mockMvc.perform( post( "/api/v1/funding-events/{id}/arm", fundingEventId )
                .contentType( MediaType.APPLICATION_JSON )
                .content( armFundingEventBody ) )
            .andExpect( status().isCreated() )
            .andExpect( jsonPath( "$.state" ).value( "ARMED" ) )
            .andExpect( jsonPath( "$.fundingEventId" ).value( fundingEventId ) )
            .andExpect( jsonPath( "$.venue" ).value( "gate" ) )
            .andExpect( jsonPath( "$.symbol" ).value( "BTC/USDT" ) )
            .andExpect( jsonPath( "$.armSource" ).value( "EVENT_API" ) )
            .andExpect( jsonPath( "$.intendedSide" ).value( "SHORT" ) )
            .andExpect( jsonPath( "$.entryAttemptCount" ).value( 4 ) )
            .andExpect( jsonPath( "$.entrySpacingMs" ).value( 125 ) )
            .andExpect( jsonPath( "$.measuredEntryLatencyMs" ).value( 50 ) )
            .andExpect( jsonPath( "$.manualLatencyAdjustmentMs" ).value( 15 ) )
            .andExpect( jsonPath( "$.effectiveEntryLatencyMs" ).value( 65 ) )
            .andExpect( jsonPath( "$.entryLeadMs" ).value( 10000 ) )
            .andExpect( jsonPath( "$.exitLeadMs" ).value( -60000 ) );

        mockMvc.perform( get( "/api/v1/armed-trades" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].fundingEventId" ).value( fundingEventId ) )
            .andExpect( jsonPath( "$[0].venue" ).value( "gate" ) )
            .andExpect( jsonPath( "$[0].symbol" ).value( "BTC/USDT" ) );

        Long armedTradeId = armedTradeRepository.findAll().getFirst().getId();
        mockMvc.perform( get( "/api/v1/funding-events/{id}/journal", fundingEventId ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].eventCode" ).value( "FUNDING_EVENT_CREATED" ) )
            .andExpect( jsonPath( "$[1].eventCode" ).value( "FUNDING_EVENT_ARMED" ) );

        mockMvc.perform( get( "/api/v1/armed-trades/{id}/journal", armedTradeId ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].eventCode" ).value( "ARMED_TRADE_CREATED" ) );

        assertThat( armedTradeRepository.findAll() ).hasSize( 1 );
        assertThat( fundingEventRepository.findById( fundingEventId ) ).get().extracting( "status" ).isEqualTo( com.crypto.funding.domain.event.FundingEventStatus.ARMED );
    }

    @Test
    void rejectsLongFundingArmedTrade() throws Exception
    {
        mockMvc.perform( post( "/api/v1/funding-events" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "venue":"gate",
                      "symbol":"BTC/USDT",
                      "fundingTime":"2030-01-01T00:00:00Z",
                      "fundingRatePct":0.015,
                      "sourceType":"manual",
                      "sourceRef":"long-rejection-test"
                    }
                    """ ) )
            .andExpect( status().isCreated() );

        Long fundingEventId = fundingEventRepository.findAll().getFirst().getId();

        mockMvc.perform( post( "/api/v1/funding-events/{id}/arm", fundingEventId )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "notionalUsd": 25,
                      "intendedSide": "LONG",
                      "plannedEntryAt": "2029-12-31T23:59:50Z",
                      "plannedExitAt": "2030-01-01T00:01:00Z"
                    }
                    """ ) )
            .andExpect( status().isConflict() )
            .andExpect( jsonPath( "$.message" ).value( "Funding trades support SHORT side only." ) );
    }

    @Test
    void returnsValidationAndNotFoundErrors() throws Exception
    {
        mockMvc.perform( post( "/api/v1/funding-events" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( "{\"venue\":\"\",\"symbol\":\"BTC/USDT\",\"fundingTime\":\"2030-01-01T00:00:00Z\"}" ) )
            .andExpect( status().isBadRequest() )
            .andExpect( jsonPath( "$.message" ).exists() );

        mockMvc.perform( post( "/api/v1/funding-events" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( "{\"venue\":\"gate\",\"symbol\":\"BTC/USDT\",\"fundingTime\":\"2020-01-01T00:00:00Z\"}" ) )
            .andExpect( status().isConflict() )
            .andExpect( jsonPath( "$.message" ).value( "Время фандинга должно быть в будущем." ) );

        mockMvc.perform( post( "/api/v1/armed-trades" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( "{\"fundingEventId\":999,\"notionalUsd\":25}" ) )
            .andExpect( status().isNotFound() );
    }

    @Test
    void listsAndReviewsCandidatesViaApi() throws Exception
    {
        Long candidateId = signalCandidateIngestService.ingest( new IngestSignalCandidateCommand(
            "FUNDING_API",
            123L,
            456L,
            "coin: KERNEL/USDT:USDT",
            "gate",
            "KERNEL/USDT",
            java.time.Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2030-01-01T08:00:00Z" ),
            BigDecimal.valueOf( 0.0125 )
        ) ).id();

        mockMvc.perform( get( "/api/v1/candidates" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.content[0].id" ).value( candidateId ) )
            .andExpect( jsonPath( "$.content[0].status" ).value( "NORMALIZED" ) );

        mockMvc.perform( get( "/api/v1/candidates/{id}", candidateId ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.suggestedVenue" ).value( "gate" ) )
            .andExpect( jsonPath( "$.suggestedFundingTime" ).value( "2030-01-01T08:00:00Z" ) )
            .andExpect( jsonPath( "$.suggestedFundingRatePct" ).value( 0.0125 ) );

        mockMvc.perform( post( "/api/v1/candidates/{id}/approve", candidateId )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "venue":"gate",
                      "fundingTime":"2030-01-01T08:00:00Z",
                      "fundingRatePct":0.0125,
                      "reviewNotes":"looks valid"
                    }
                    """ ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.status" ).value( "EVENT_CREATED" ) )
            .andExpect( jsonPath( "$.fundingEventId" ).isNumber() );

        Long fundingEventId = fundingEventRepository.findAll().getFirst().getId();

        mockMvc.perform( post( "/api/v1/funding-events/{id}/arm", fundingEventId )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "notionalUsd": 15,
                      "intendedSide": "SHORT",
                      "plannedEntryAt": "2030-01-01T07:59:45Z",
                      "plannedExitAt": "2030-01-01T08:00:15Z",
                      "notes": "reviewed and armed"
                    }
                    """ ) )
            .andExpect( status().isCreated() )
            .andExpect( jsonPath( "$.fundingEventId" ).value( fundingEventId ) )
            .andExpect( jsonPath( "$.venue" ).value( "gate" ) )
            .andExpect( jsonPath( "$.symbol" ).value( "KERNEL/USDT" ) )
            .andExpect( jsonPath( "$.state" ).value( "ARMED" ) )
            .andExpect( jsonPath( "$.entryAttemptCount" ).value( 1 ) )
            .andExpect( jsonPath( "$.entrySpacingMs" ).value( 0 ) )
            .andExpect( jsonPath( "$.manualLatencyAdjustmentMs" ).value( 0 ) )
            .andExpect( jsonPath( "$.effectiveEntryLatencyMs" ).value( 0 ) );

        mockMvc.perform( get( "/api/v1/funding-events" ).param( "candidateId", candidateId.toString() ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.content[0].signalCandidateId" ).value( candidateId ) )
            .andExpect( jsonPath( "$.content[0].venue" ).value( "gate" ) );

        Long rejectedCandidateId = signalCandidateIngestService.ingest( new IngestSignalCandidateCommand(
            "FUNDING_API",
            123L,
            789L,
            "coin: ME/USDT:USDT",
            "gate",
            "ME/USDT",
            java.time.Instant.parse( "2030-01-01T00:01:00Z" ),
            Instant.parse( "2030-01-01T08:00:00Z" ),
            BigDecimal.valueOf( 0.01 )
        ) ).id();

        mockMvc.perform( post( "/api/v1/candidates/{id}/reject", rejectedCandidateId )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "reviewNotes":"low confidence"
                    }
                    """ ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.status" ).value( "REJECTED" ) )
            .andExpect( jsonPath( "$.reviewDecision" ).value( "REJECT" ) );
    }

    @Test
    void deletingCandidateCleansLinkedEventAndPreparedTrade()
        throws Exception
    {
        Long candidateId = signalCandidateIngestService.ingest( ManualSignalTestSupport.manualSignal(
            999L,
            1001L,
            "gate",
            "DRIFT/USDT",
            Instant.parse( "2030-01-01T08:00:00Z" ),
            Instant.parse( "2030-01-01T09:00:00Z" ),
            BigDecimal.valueOf( -0.021 )
        ) ).id();

        mockMvc.perform( post( "/api/v1/candidates/{id}/approve", candidateId )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "venue":"gate",
                      "fundingTime":"2030-01-01T09:00:00Z",
                      "fundingRatePct":-0.021,
                      "reviewNotes":"manual flow"
                    }
                    """ ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.status" ).value( "EVENT_CREATED" ) );

        Long fundingEventId = fundingEventRepository.findAll().getFirst().getId();

        mockMvc.perform( post( "/api/v1/funding-events/{id}/arm", fundingEventId )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "notionalUsd": 30,
                      "intendedSide": "SHORT",
                      "plannedEntryAt": "2030-01-01T08:58:50Z",
                      "plannedExitAt": "2030-01-01T09:00:20Z",
                      "notes": "delete cascade"
                    }
                    """ ) )
            .andExpect( status().isCreated() )
            .andExpect( jsonPath( "$.signalCandidateId" ).value( candidateId ) );

        mockMvc.perform( delete( "/api/v1/candidates/{id}", candidateId ).param( "note", "operator cleanup" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.status" ).value( "DELETED" ) )
            .andExpect( jsonPath( "$.reviewNotes" ).value( "operator cleanup" ) );

        assertThat( fundingEventRepository.findById( fundingEventId ) ).isEmpty();
        assertThat( armedTradeRepository.findAll() ).isEmpty();

        mockMvc.perform( get( "/api/v1/candidates" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.content[?(@.id==" + candidateId + ")]" ).isEmpty() );
    }

    @Test
    void hidesExpiredFundingEventsFromDefaultListAndMarksThemExpired() throws Exception
    {
        FundingEventEntity staleEvent = new FundingEventEntity();
        staleEvent.setVenue( "gate" );
        staleEvent.setSymbol( "EDGE/USDT" );
        staleEvent.setFundingTime( java.time.Instant.parse( "2020-01-01T00:00:00Z" ) );
        staleEvent.setFundingRatePct( java.math.BigDecimal.valueOf( -0.05 ) );
        staleEvent.setStatus( FundingEventStatus.DISCOVERED );
        staleEvent.setSourceType( "funding_api" );
        staleEvent.setSourceRef( "stale" );
        staleEvent.setDiscoveredAt( java.time.Instant.parse( "2019-12-31T23:00:00Z" ) );
        staleEvent = fundingEventRepository.save( staleEvent );

        mockMvc.perform( get( "/api/v1/funding-events" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.content" ).isArray() )
            .andExpect( jsonPath( "$.content[?(@.id==" + staleEvent.getId() + ")]" ).isEmpty() );

        assertThat( fundingEventRepository.findById( staleEvent.getId() ) )
            .get()
            .extracting( FundingEventEntity::getStatus )
            .isEqualTo( FundingEventStatus.EXPIRED );
    }

    @Test
    void hidesPreparedTradesLinkedToExpiredFundingEvents() throws Exception
    {
        FundingEventEntity staleEvent = new FundingEventEntity();
        staleEvent.setVenue( "gate" );
        staleEvent.setSymbol( "NOM/USDT" );
        staleEvent.setFundingTime( java.time.Instant.parse( "2020-01-01T00:00:00Z" ) );
        staleEvent.setFundingRatePct( java.math.BigDecimal.valueOf( -0.01 ) );
        staleEvent.setStatus( FundingEventStatus.ARMED );
        staleEvent.setSourceType( "funding_api" );
        staleEvent.setSourceRef( "stale-trade" );
        staleEvent.setDiscoveredAt( java.time.Instant.parse( "2019-12-31T23:00:00Z" ) );
        staleEvent = fundingEventRepository.save( staleEvent );

        ArmedTradeEntity staleTrade = new ArmedTradeEntity();
        staleTrade.setFundingEventId( staleEvent.getId() );
        staleTrade.setNotionalUsd( java.math.BigDecimal.valueOf( 25 ) );
        staleTrade.setIntendedSide( TradeSide.SHORT );
        staleTrade.setPlannedEntryAt( java.time.Instant.parse( "2019-12-31T23:59:30Z" ) );
        staleTrade.setPlannedExitAt( java.time.Instant.parse( "2020-01-01T00:00:30Z" ) );
        staleTrade.setArmedAt( java.time.Instant.parse( "2019-12-31T22:00:00Z" ) );
        staleTrade.setArmSource( TradeArmSource.EVENT_API );
        staleTrade.setState( ArmedTradeState.ARMED );
        staleTrade.setNotes( "stale" );
        armedTradeRepository.save( staleTrade );

        mockMvc.perform( get( "/api/v1/armed-trades" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$" ).isArray() )
            .andExpect( jsonPath( "$[?(@.fundingEventId==" + staleEvent.getId() + ")]" ).isEmpty() );

        assertThat( fundingEventRepository.findById( staleEvent.getId() ) )
            .get()
            .extracting( FundingEventEntity::getStatus )
            .isEqualTo( FundingEventStatus.EXPIRED );
    }
}
