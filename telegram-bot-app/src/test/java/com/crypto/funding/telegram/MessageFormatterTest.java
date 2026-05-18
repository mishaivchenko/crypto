package com.crypto.funding.telegram;

import com.crypto.funding.telegram.bot.MessageFormatter;
import com.crypto.funding.telegram.client.dto.CandidateSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageFormatterTest
{
    @Test
    void welcomeContainsName()
    {
        String msg = MessageFormatter.welcome( "Alice" );
        assertThat( msg ).contains( "Alice" );
    }

    @Test
    void escapeMarkdownHandlesSpecialChars()
    {
        String result = MessageFormatter.escapeMarkdown( "BTC-USDT (3.5%)" );
        assertThat( result ).contains( "\\(" ).contains( "\\)" ).contains( "\\-" ).contains( "\\." );
    }

    @Test
    void newSignalAlertContainsSymbol()
    {
        CandidateSummary candidate = new CandidateSummary(
            1L, "api", null, null, "gate", "BTCUSDT", "BTCUSDT",
            List.of(), Instant.now(), "NORMALIZED", null,
            Instant.now(), new BigDecimal( "0.032" ), null, null
        );

        String alert = MessageFormatter.newSignalAlert( candidate, "http://localhost:8090" );
        assertThat( alert ).contains( "BTCUSDT" );
        assertThat( alert ).contains( "gate" );
    }

    @Test
    void signalsMessageHandlesEmptyList()
    {
        String msg = MessageFormatter.signalsMessage( List.of() );
        assertThat( msg ).contains( "нет" );
    }
}
