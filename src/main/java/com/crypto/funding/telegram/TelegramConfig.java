package com.crypto.funding.telegram;

import com.crypto.funding.api.TelegramBot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TelegramConfig
{
    @Bean
    @ConditionalOnProperty(prefix = "telegram.bot", name = "enabled", havingValue = "true")
    public TelegramBotsApi telegramBotsApi( TelegramBot telegramBot )
    {
        TelegramBotsApi telegramBotsApi;
        try
        {
            telegramBotsApi = new TelegramBotsApi( DefaultBotSession.class );
            telegramBotsApi.registerBot( telegramBot );
        }
        catch( TelegramApiException e )
        {
            throw new RuntimeException( e );
        }

        return telegramBotsApi;
    }
}
