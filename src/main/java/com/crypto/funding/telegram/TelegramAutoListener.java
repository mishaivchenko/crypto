package com.crypto.funding.telegram;

import com.crypto.funding.application.candidate.IngestSignalCandidateCommand;
import com.crypto.funding.application.candidate.SignalCandidateIngestService;
import com.crypto.funding.telegram.parser.ArbitrageSignalParser;
import com.crypto.funding.telegram.parser.FundingSignalParser;
import com.crypto.funding.watchlist.ArbitrageWatchlistService;
import com.crypto.funding.watchlist.FundingWatchlistService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelegramAutoListener
{

    private final TelegramReaderService reader;
    private final ArbitrageWatchlistService arbitrageWatchlist;
    private final FundingWatchlistService fundingWatchlist;
    private final SignalCandidateIngestService signalCandidateIngestService;
    private final ArbitrageSignalParser arbitrageSignalParser;
    private final FundingSignalParser fundingSignalParser;
    private final String channel;
    private final int defaultTtlMinutes;

    public TelegramAutoListener( TelegramReaderService reader,
        ArbitrageWatchlistService watchlist,
        FundingWatchlistService fundingWatchlist,
        SignalCandidateIngestService signalCandidateIngestService,
        ArbitrageSignalParser arbitrageSignalParser,
        FundingSignalParser fundingSignalParser,
        @Value( "${arb.telegram.channel:@funding_watchdog}" ) String channel,
        @Value( "${arb.watchlist.defaultTtlMinutes:45}" ) int defaultTtlMinutes )
    {
        this.reader = reader;
        this.arbitrageWatchlist = watchlist;
        this.fundingWatchlist = fundingWatchlist;
        this.signalCandidateIngestService = signalCandidateIngestService;
        this.arbitrageSignalParser = arbitrageSignalParser;
        this.fundingSignalParser = fundingSignalParser;
        this.channel = channel;
        this.defaultTtlMinutes = defaultTtlMinutes;
    }

    @EventListener( ApplicationReadyEvent.class )
    public void start()
    {
        var chatIdOpt = reader.resolveAndEnsureJoin( channel );
        if( chatIdOpt.isEmpty() )
        {
            System.err.println( "[TG] Can't resolve/join channel: " + channel );
            return;
        }
        long chatId = chatIdOpt.get();//

        System.out.println( "[TG] Listening channel " + channel + " (chatId=" + chatId + ")" );

        reader.onNewMessages( chatId, message -> {
            String text = message.text();

            List<String> arbSymbols = arbitrageSignalParser.extractSymbols(text);
            for (String sym : arbSymbols) {
                arbitrageWatchlist.addSymbol(sym);
            }

            // 2. Попробовать достать монеты, помеченные как "фандинг"
            List<String> fundSymbols = fundingSignalParser.extractSymbols( text );
            for (String sym : fundSymbols) {
                fundingWatchlist.addSymbol(sym);
                signalCandidateIngestService.ingest( new IngestSignalCandidateCommand(
                    "TELEGRAM",
                    message.chatId(),
                    message.messageId(),
                    text,
                    sym,
                    message.receivedAt()
                ) );
            }

        } );
    }
}
