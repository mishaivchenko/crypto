package com.crypto.funding.api;

import com.crypto.funding.application.candidate.IngestSignalCandidateCommand;
import com.crypto.funding.application.candidate.SignalCandidateIngestService;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:sqlite:./build/test-new-domain-api.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect"
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

    @BeforeEach
    void clean()
    {
        armedTradeRepository.deleteAll();
        fundingEventRepository.deleteAll();
        signalCandidateRepository.deleteAll();
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
              "intendedSide": "LONG",
              "plannedEntryAt": "2030-01-01T00:00:10Z",
              "plannedExitAt": "2030-01-01T00:01:00Z",
              "notes": "manual arm"
            }
            """;

        mockMvc.perform( post( "/api/v1/funding-events/{id}/arm", fundingEventId )
                .contentType( MediaType.APPLICATION_JSON )
                .content( armFundingEventBody ) )
            .andExpect( status().isCreated() )
            .andExpect( jsonPath( "$.state" ).value( "ARMED" ) )
            .andExpect( jsonPath( "$.fundingEventId" ).value( fundingEventId ) )
            .andExpect( jsonPath( "$.armSource" ).value( "EVENT_API" ) );

        mockMvc.perform( get( "/api/v1/armed-trades" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].fundingEventId" ).value( fundingEventId ) );

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
    void returnsValidationAndNotFoundErrors() throws Exception
    {
        mockMvc.perform( post( "/api/v1/funding-events" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( "{\"venue\":\"\",\"symbol\":\"BTC/USDT\",\"fundingTime\":\"2030-01-01T00:00:00Z\"}" ) )
            .andExpect( status().isBadRequest() )
            .andExpect( jsonPath( "$.message" ).exists() );

        mockMvc.perform( post( "/api/v1/armed-trades" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( "{\"fundingEventId\":999,\"notionalUsd\":25}" ) )
            .andExpect( status().isNotFound() );
    }

    @Test
    void blocksLegacyTestOrderEndpointByDefault() throws Exception
    {
        mockMvc.perform( post( "/api/test-orders" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "exchange":"bybit",
                      "symbolUnified":"BTCUSDT",
                      "side":"BUY",
                      "type":"MARKET",
                      "quantity":1
                    }
                    """ ) )
            .andExpect( status().isConflict() )
            .andExpect( jsonPath( "$.message" ).value( org.hamcrest.Matchers.containsString( "legacy execution blocked" ) ) );
    }

    @Test
    void listsAndReviewsCandidatesViaApi() throws Exception
    {
        Long candidateId = signalCandidateIngestService.ingest( new IngestSignalCandidateCommand(
            "TELEGRAM",
            123L,
            456L,
            "coin: KERNEL/USDT:USDT",
            "KERNEL/USDT",
            java.time.Instant.parse( "2030-01-01T00:00:00Z" )
        ) ).id();

        mockMvc.perform( get( "/api/v1/candidates" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.content[0].id" ).value( candidateId ) )
            .andExpect( jsonPath( "$.content[0].status" ).value( "NORMALIZED" ) );

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

        mockMvc.perform( get( "/api/v1/funding-events" ).param( "candidateId", candidateId.toString() ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.content[0].signalCandidateId" ).value( candidateId ) )
            .andExpect( jsonPath( "$.content[0].venue" ).value( "gate" ) );

        Long rejectedCandidateId = signalCandidateIngestService.ingest( new IngestSignalCandidateCommand(
            "TELEGRAM",
            123L,
            789L,
            "coin: ME/USDT:USDT",
            "ME/USDT",
            java.time.Instant.parse( "2030-01-01T00:01:00Z" )
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
}
