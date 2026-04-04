package com.crypto.funding.scheduler;

import com.crypto.funding.CryptoApplication;
import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import com.crypto.funding.persistence.service.ApprovedFundingStore;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(
    classes = CryptoApplication.class,
    properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:sqlite:file:./build/funding-e2e.db?busy_timeout=5000&journal_mode=WAL",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-init-sql=PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000; PRAGMA synchronous=NORMAL; PRAGMA foreign_keys=ON;",
        "funding.latency-probe.enabled=false",
        "funding.scheduler.lookahead-seconds=5",
        "funding.scheduler.min-recheck-millis=50",
        "funding.scheduler.discovery-interval-seconds=1",
        "funding.scheduler.max-lateness-seconds=120",
        "funding.execution-delay-seconds=1",
        "trading.execution.mode=LIVE",
        "trading.execution.legacy-enabled=true",
        "trading.execution.live-venues=bybit",
        "trading.bybit.api-key=test-key",
        "trading.bybit.secret-key=test-secret"
    }
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FundingFlowIntegrationTest {

    private static final WireMockServer wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    @Autowired
    private ApprovedFundingStore store;

    @Autowired
    private ApprovedFundingRepository repository;

    @Autowired
    private FundingSchedulerService scheduler;

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        if (!wireMock.isRunning()) {
            wireMock.start();
        }
        registry.add("trading.bybit.base-url", wireMock::baseUrl);
    }

    @BeforeAll
    void setUp() {
        configureFor("localhost", wireMock.port());
    }

    @AfterAll
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void fundingStoredInDbTriggersExchangeOrderWhenExplicitlyEnabled() {
        wireMock.resetAll();

        Instant nextFundingAt = Instant.now().plusSeconds(2);
        stubBybitFunding(nextFundingAt);
        stubBybitInstruments();
        stubBybitOrderCreate();

        store.approve("BTC/USDT", Set.of("bybit"), new BigDecimal("50"), nextFundingAt);
        scheduler.wakeup("test");

        Awaitility.await()
            .atMost(Duration.ofSeconds(8))
            .until(() -> repository.findBySymbol("BTC/USDT")
                .map(ApprovedFundingEntity::isExecuted)
                .orElse(false));

        ApprovedFundingEntity saved = repository.findBySymbol("BTC/USDT").orElseThrow();
        assertThat(saved.getExecutedAt()).isNotNull();

        verify(1, getRequestedFor(urlPathEqualTo("/v5/market/tickers"))
            .withQueryParam("category", equalTo("linear"))
            .withQueryParam("symbol", equalTo("BTCUSDT")));

        verify(1, getRequestedFor(urlPathEqualTo("/v5/market/instruments-info"))
            .withQueryParam("category", equalTo("linear"))
            .withQueryParam("symbol", equalTo("BTCUSDT")));

        verify(1, postRequestedFor(urlEqualTo("/v5/order/create")));
    }

    private void stubBybitFunding(Instant nextFundingAt) {
        wireMock.stubFor(get(urlPathEqualTo("/v5/market/tickers"))
            .withQueryParam("category", equalTo("linear"))
            .withQueryParam("symbol", equalTo("BTCUSDT"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"result":{"list":[{"symbol":"BTCUSDT","fundingRate":"0.0001","indexPrice":"30000","nextFundingTime":"%s"}]}}
                    """.formatted(nextFundingAt.toEpochMilli()))));
    }

    private void stubBybitInstruments() {
        wireMock.stubFor(get(urlPathEqualTo("/v5/market/instruments-info"))
            .withQueryParam("category", equalTo("linear"))
            .withQueryParam("symbol", equalTo("BTCUSDT"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"result":{"list":[{"symbol":"BTCUSDT","lotSizeFilter":{"minOrderQty":"0.001","qtyStep":"0.001","minNotionalValue":"5"}}]}}
                    """)));
    }

    private void stubBybitOrderCreate() {
        wireMock.stubFor(post(urlEqualTo("/v5/order/create"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"retCode":0,"retMsg":"OK","result":{"orderId":"oid-42","orderStatus":"Filled","avgPrice":"30000"}}
                    """)));
    }
}
