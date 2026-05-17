package com.crypto.funding.application.engine.planning;

import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeArmSource;
import com.crypto.funding.domain.trade.TradeSide;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EnginePlanSummaryFormatterTest
{
    private final EnginePlanSummaryFormatter formatter = new EnginePlanSummaryFormatter();

    @Test
    void formatsCurrentOperatorFacingStatusText()
    {
        ArmedTrade trade = new ArmedTrade(
            1L,
            11L,
            BigDecimal.valueOf( 25 ),
            TradeSide.SHORT,
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2030-01-01T00:02:00Z" ),
            Instant.parse( "2029-12-31T23:30:00Z" ),
            null,
            null,
            null,
            1,
            0L,
            25L,
            0L,
            25L,
            TradeArmSource.EVENT_API,
            ArmedTradeState.ARMED,
            null,
            null,
            null,
            null,
            Instant.parse( "2029-12-31T23:00:00Z" ),
            Instant.parse( "2029-12-31T23:00:00Z" )
        );
        FundingEventEntity event = new FundingEventEntity();
        event.setVenue( "gate" );
        event.setSymbol( "WET/USDT" );

        assertThat( formatter.format( trade, event, EnginePlanStatus.WAITING_ENTRY ) ).isEqualTo( "Ожидаем вход по WET/USDT на gate" );
        assertThat( formatter.format( trade, event, EnginePlanStatus.ENTRY_WINDOW ) ).isEqualTo( "Окно входа активно для WET/USDT (SHORT)" );
        assertThat( formatter.format( trade, event, EnginePlanStatus.WAITING_EXIT ) ).isEqualTo( "Сделка ждёт планового выхода" );
        assertThat( formatter.format( trade, event, EnginePlanStatus.EXIT_WINDOW ) ).isEqualTo( "Окно выхода активно для WET/USDT" );
        assertThat( formatter.format( trade, event, EnginePlanStatus.OVERDUE ) ).isEqualTo( "Окно входа пропущено, нужен разбор оператора" );
        assertThat( formatter.format( trade, event, EnginePlanStatus.CLOSED ) ).isEqualTo( "Сделка уже закрыта" );
        assertThat( formatter.format( trade, event, EnginePlanStatus.INVALID ) ).isEqualTo( "План сделки неполный и не готов к исполнению" );
    }
}
