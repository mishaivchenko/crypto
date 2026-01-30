package com.crypto.funding.persistence.service;

import com.crypto.funding.persistence.service.FundingApprovalService;
import com.crypto.funding.persistence.service.ApprovedFundingStore;
import com.crypto.funding.scheduler.FundingSchedulerService;
import com.crypto.funding.watchlist.FundingWatchlistService;
import com.crypto.funding.watchlist.FundingWatchlistService.Item;
import com.crypto.funding.watchlist.FundingWatchlistService.WatchFunding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundingApprovalServiceTest {

    @Mock
    private ApprovedFundingStore store;
    @Mock
    private FundingWatchlistService watchlist;
    @Mock
    private FundingSchedulerService scheduler;

    @Test
    void throwsIfNoFundingDataForSymbol() {
        FundingApprovalService service = new FundingApprovalService(store, watchlist, scheduler);

        when(watchlist.all()).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> service.approve("BTC/USDT", Set.of("binance"), BigDecimal.TEN))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Funding data not found");

        verify(store, never()).approve(any(), any(), any(), any());
        verifyNoInteractions(scheduler);
    }

    @Test
    void resolvesNextFundingFromSelectedExchange() {
        FundingApprovalService service = new FundingApprovalService(store, watchlist, scheduler);
        Instant next = Instant.now().plusSeconds(120);
        Item item = new Item("BTC/USDT", Instant.now().plusSeconds(3600),
            Map.of("binance", new WatchFunding("binance", 0.01, next, 120, Instant.now())));

        when(watchlist.all()).thenReturn(java.util.List.of(item));

        service.approve("BTC/USDT", Set.of("binance"), new BigDecimal("25"));

        verify(store).approve(eq("BTC/USDT"), eq(Set.of("binance")), eq(new BigDecimal("25")), eq(next));
        verify(scheduler).wakeup(contains("approve BTC/USDT"));
    }
}
