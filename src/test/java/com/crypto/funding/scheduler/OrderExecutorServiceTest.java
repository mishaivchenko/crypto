package com.crypto.funding.scheduler;

import com.crypto.funding.exchanges.ExchangeRestClient;
import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import com.crypto.funding.trading.PlaceTestOrderCommand;
import com.crypto.funding.trading.TestOrderEngine;
import com.crypto.funding.trading.TestOrderResult;
import com.crypto.funding.watchlist.FundingInfo;
import com.crypto.funding.watchlist.SymbolRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExecutorServiceTest {

    @Mock
    private TestOrderEngine testOrderEngine;

    @Mock
    private ApprovedFundingRepository repo;

    @Mock
    private ExchangeRestClient binance;

    private OrderExecutorService service;

    @BeforeEach
    void setUp() {
        service = new OrderExecutorService(testOrderEngine, repo, List.of(binance));
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
        when(binance.name()).thenReturn("binance");
        when(binance.fetchFunding("BTCUSDT"))
            .thenReturn(new FundingInfo("binance", "BTCUSDT", 0.01, Instant.now().plusSeconds(60), 60, new BigDecimal("10")));
        when(binance.fetchRules("BTCUSDT"))
            .thenReturn(new SymbolRules(new BigDecimal("0.001"), new BigDecimal("0.001"), null));

        TestOrderResult mockResult = new TestOrderResult("binance", "123", "BTCUSDT", null, null, BigDecimal.ONE, null, "FILLED", System.currentTimeMillis());
        when(testOrderEngine.placeTestOrder(any(PlaceTestOrderCommand.class))).thenReturn(mockResult);

        service.executeOnce(1L);

        verify(testOrderEngine, times(1)).placeTestOrder(any());
        ArgumentCaptor<ApprovedFundingEntity> captor = ArgumentCaptor.forClass(ApprovedFundingEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().isExecuted()).isTrue();
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
        when(binance.name()).thenReturn("binance");
        when(binance.fetchFunding("XRPUSDT"))
            .thenReturn(new FundingInfo("binance", "XRPUSDT", 0.01, Instant.now().plusSeconds(60), 60, new BigDecimal("1")));
        when(binance.fetchRules("XRPUSDT"))
            .thenReturn(new SymbolRules(new BigDecimal("5"), new BigDecimal("0.1"), null));

        assertThatThrownBy(() -> service.executeOnce(3L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Too small");

        verify(repo, never()).save(any());
        assertThat(entity.isExecuted()).isFalse();
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
        verify(repo).save(entity);
    }
}
