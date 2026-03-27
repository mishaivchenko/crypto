package com.crypto.funding.api;

import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
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
    "telegram.enabled=false",
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

    @BeforeEach
    void clean()
    {
        armedTradeRepository.deleteAll();
        fundingEventRepository.deleteAll();
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

        String armedTradeBody = """
            {
              "fundingEventId": %d,
              "notionalUsd": 25,
              "intendedSide": "LONG",
              "plannedEntryAt": "2030-01-01T00:00:10Z",
              "plannedExitAt": "2030-01-01T00:01:00Z",
              "notes": "manual arm"
            }
            """.formatted( fundingEventId );

        mockMvc.perform( post( "/api/v1/armed-trades" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( armedTradeBody ) )
            .andExpect( status().isCreated() )
            .andExpect( jsonPath( "$.state" ).value( "ARMED" ) )
            .andExpect( jsonPath( "$.fundingEventId" ).value( fundingEventId ) );

        mockMvc.perform( get( "/api/v1/armed-trades" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].fundingEventId" ).value( fundingEventId ) );

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
}
