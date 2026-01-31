package com.crypto.funding.scheduler;

import com.crypto.funding.exchanges.AbstractRestClient;
import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import com.crypto.funding.trading.PlaceTestOrderCommand;
import com.crypto.funding.trading.TestOrderEngine;
import com.crypto.funding.trading.TestOrderResult;
import com.crypto.funding.trading.OrderSide;
import com.crypto.funding.trading.OrderType;
import com.crypto.funding.watchlist.FundingInfo;
import com.crypto.funding.watchlist.SymbolRules;
import com.crypto.funding.scheduler.NetworkLatencyService.ThrowingSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderExecutorServiceTest {

    @Mock
    private TestOrderEngine testOrderEngine;

    @Mock
    private ApprovedFundingRepository repo;

    @Mock
    private AbstractRestClient binance;

    @Mock
    private NetworkLatencyService latencyService;

    private OrderExecutorService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new OrderExecutorService(testOrderEngine, repo, List.of(binance), latencyService, true);
        when(latencyService.measureAndRecord(eq("binance"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            ThrowingSupplier<TestOrderResult> supplier = inv.getArgument(1);
            return supplier.get();
        });
        lenient().when(latencyService.estimate("binance")).thenReturn(Duration.ZERO);
        lenient().when(binance.exchangeName()).thenReturn("binance");
        lenient().when(binance.getApiKey()).thenReturn("k");
        lenient().when(binance.getSecretKey()).thenReturn("s");
        lenient().when(binance.getBaseUrl()).thenReturn("http://localhost");
    }

    @Test
    void executesHappyPathAndMarksExecuted() throws Exception {
        ApprovedFundingEntity entity = new ApprovedFundingEntity(
            "BTC/USDT",
            Set.of("binance"),
            new BigDecimal("100"),
            Instant.now().plusSeconds(60)
        );
        entity.setActive(true);
        entity.setExecuted(false);

        when(repo.findById(1L)).thenReturn(Optional.of(entity));
        when(binance.exchangeName()).thenReturn("binance");
        when(binance.fetchFunding("BTCUSDT"))
            .thenReturn(new FundingInfo("binance", "BTCUSDT", 0.01, Instant.now().plusMillis(20), 1, new BigDecimal("10")));
        when(binance.fetchRules("BTCUSDT"))
            .thenReturn(new SymbolRules(new BigDecimal("0.001"), new BigDecimal("0.001"), null));

        TestOrderResult mockResult = new TestOrderResult("binance", "123", "BTCUSDT", null, null, BigDecimal.ONE, null, "FILLED", System.currentTimeMillis());
        when(testOrderEngine.placeTestOrder(any(PlaceTestOrderCommand.class))).thenReturn(mockResult);

        service.executeOnce(1L);

        verify(latencyService).measureAndRecord(eq("binance"), any());
        verify(testOrderEngine, times(2)).placeTestOrder(any()); // пинг + основной
        ArgumentCaptor<ApprovedFundingEntity> captor = ArgumentCaptor.forClass(ApprovedFundingEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().isExecuted()).isTrue();
        assertThat(captor.getValue().getExecutedAt()).isNotNull();
    }

    @Test
    void skipsWhenExchangesEmptyAndMarksExecuted() throws Exception {
        ApprovedFundingEntity entity = new ApprovedFundingEntity(
            "ETH/USDT",
            Set.of(), // пусто
            new BigDecimal("50"),
            Instant.now().plusSeconds(30)
        );
        entity.setActive(true);
        entity.setExecuted(false);

        when(repo.findById(2L)).thenReturn(Optional.of(entity));

        service.executeOnce(2L);

        verify(testOrderEngine, never()).placeTestOrder(any());
        ArgumentCaptor<ApprovedFundingEntity> captor = ArgumentCaptor.forClass(ApprovedFundingEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().isExecuted()).isTrue();
    }

    @Test
    void throwsIfQuantityBelowMinQty() throws Exception {
        ApprovedFundingEntity entity = new ApprovedFundingEntity(
            "XRP/USDT",
            Set.of("binance"),
            new BigDecimal("2"), // мало для minQty
            Instant.now().plusSeconds(30)
        );
        entity.setActive(true);
        entity.setExecuted(false);

        when(repo.findById(3L)).thenReturn(Optional.of(entity));
        when(binance.exchangeName()).thenReturn("binance");
        when(binance.fetchFunding("XRPUSDT"))
            .thenReturn(new FundingInfo("binance", "XRPUSDT", 0.01, Instant.now().plusMillis(20), 1, new BigDecimal("1")));
        when(binance.fetchRules("XRPUSDT"))
            .thenReturn(new SymbolRules(new BigDecimal("5"), new BigDecimal("0.1"), null));

        // min qty = 5, min notional not set; provided USDT=2 means we fail before hitting network

        assertThatThrownBy(() -> service.executeOnce(3L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Too small");

        verify(latencyService, never()).measureAndRecord(any(), any());
        verify(testOrderEngine, never()).placeTestOrder(any());
        verify(repo, never()).save(any());
        assertThat(entity.isExecuted()).isFalse();
    }

    @Test
    void respectsMinNotionalWhenHigherThanMinQty() throws Exception {
        ApprovedFundingEntity entity = new ApprovedFundingEntity(
            "DOT/USDT",
            Set.of("binance"),
            new BigDecimal("5"), // less than min notional 6
            Instant.now().plusSeconds(30)
        );
        entity.setActive(true);
        entity.setExecuted(false);

        when(repo.findById(7L)).thenReturn(Optional.of(entity));
        when(binance.exchangeName()).thenReturn("binance");
        when(binance.fetchFunding("DOTUSDT"))
            .thenReturn(new FundingInfo("binance", "DOTUSDT", 0.01, Instant.now().plusMillis(20), 1, new BigDecimal("4")));
        when(binance.fetchRules("DOTUSDT"))
            .thenReturn(new SymbolRules(new BigDecimal("0.1"), new BigDecimal("0.1"), new BigDecimal("6")));

        assertThatThrownBy(() -> service.executeOnce(7L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Too small");

        verify(testOrderEngine, never()).placeTestOrder(any());
        verify(repo, never()).save(any());
    }

    @Test
    void markMissedSetsExecuted() {
        ApprovedFundingEntity entity = new ApprovedFundingEntity(
            "SOL/USDT",
            Set.of("binance"),
            new BigDecimal("15"),
            Instant.now().plusSeconds(10)
        );
        entity.setActive(true);
        entity.setExecuted(false);

        when(repo.findById(5L)).thenReturn(Optional.of(entity));

        service.markMissed(5L, "SOLUSDT", Instant.now().minusSeconds(50), Instant.now());

        assertThat(entity.isExecuted()).isTrue();
        assertThat(entity.getExecutedAt()).isNotNull();
        verify(repo).save(entity);
    }

    @Test
    void skipsLatencyProbeWhenDisabled() throws Exception {
        OrderExecutorService noProbeService = new OrderExecutorService(testOrderEngine, repo, List.of(binance), latencyService, false);

        ApprovedFundingEntity entity = new ApprovedFundingEntity(
            "ADA/USDT",
            Set.of("binance"),
            new BigDecimal("100"),
            Instant.now().plusSeconds(60)
        );
        entity.setActive(true);
        entity.setExecuted(false);

        when(repo.findById(6L)).thenReturn(Optional.of(entity));
        when(binance.exchangeName()).thenReturn("binance");
        when(binance.fetchFunding("ADAUSDT"))
            .thenReturn(new FundingInfo("binance", "ADAUSDT", 0.01, Instant.now().plusMillis(20), 1, new BigDecimal("0.5")));
        when(binance.fetchRules("ADAUSDT"))
            .thenReturn(new SymbolRules(new BigDecimal("1"), new BigDecimal("1"), null));

        TestOrderResult mockResult = new TestOrderResult("binance", "123", "ADAUSDT", null, null, BigDecimal.ONE, null, "FILLED", System.currentTimeMillis());
        when(testOrderEngine.placeTestOrder(any(PlaceTestOrderCommand.class))).thenReturn(mockResult);

        noProbeService.executeOnce(6L);

        verify(latencyService, never()).measureAndRecord(any(), any());
        verify(testOrderEngine, times(1)).placeTestOrder(any()); // только основной
    }

    @Test
    void waitsUntilFundingWhenNoDelay() throws Exception {
        ApprovedFundingEntity entity = new ApprovedFundingEntity(
            "ATOM/USDT",
            Set.of("binance"),
            new BigDecimal("100"),
            Instant.now().plusSeconds(5)
        );
        entity.setActive(true);
        entity.setExecuted(false);

        when(repo.findById(8L)).thenReturn(Optional.of(entity));
        when(binance.exchangeName()).thenReturn("binance");
        Instant fundingAt = Instant.now().plusMillis(300);
        when(binance.fetchFunding("ATOMUSDT"))
            .thenReturn(new FundingInfo("binance", "ATOMUSDT", 0.01, fundingAt, 1, new BigDecimal("10")));
        when(binance.fetchRules("ATOMUSDT"))
            .thenReturn(new SymbolRules(new BigDecimal("0.001"), new BigDecimal("0.001"), null));

        TestOrderResult mockResult = new TestOrderResult("binance", "777", "ATOMUSDT", null, null, BigDecimal.ONE, null, "FILLED", System.currentTimeMillis());
        when(testOrderEngine.placeTestOrder(any(PlaceTestOrderCommand.class))).thenReturn(mockResult);

        long start = System.nanoTime();
        service.executeOnce(8L);
        long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertThat(elapsedMs).isGreaterThanOrEqualTo(80); // метод должен подождать до fundingAt

        verify(testOrderEngine, times(2)).placeTestOrder(any()); // probe + main
    }
}
