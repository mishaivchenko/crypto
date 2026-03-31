package com.crypto.funding.telegram.parser;

import com.crypto.funding.watchlist.FundingWatchlistService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FundingSignalParserTest
{
    private final FundingSignalParser parser = new FundingSignalParser();

    @Test
    void extractsFundingCandidateAndUpdatesWatchlist()
    {
        List<String> symbols = parser.extractSymbols( """
            Funding alert
            coin: KERNEL/USDT:USDT
            """);

        FundingWatchlistService watchlist = new FundingWatchlistService();
        symbols.forEach( watchlist::addSymbol );

        assertThat( symbols ).containsExactly( "KERNEL/USDT" );
        assertThat( watchlist.all() ).extracting( FundingWatchlistService.Item::symbol ).containsExactly( "KERNEL/USDT" );
    }
}
