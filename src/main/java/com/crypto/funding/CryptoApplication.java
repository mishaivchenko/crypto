package com.crypto.funding;

import com.crypto.funding.market.CaffeineMarketCache;
import com.crypto.funding.market.MarketCache;
import com.crypto.funding.telegram.FundingArbTelegramBot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@SpringBootApplication
@EnableScheduling
public class CryptoApplication
{

    public static void main( String[] args )
    {
        // Register our bot
        SpringApplication.run( CryptoApplication.class, args );
    }

    @Bean
    MarketCache marketCache() { return new CaffeineMarketCache(); }
}
