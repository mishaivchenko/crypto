package com.crypto.funding.telegram.config;

import com.pengrad.telegrambot.TelegramBot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelegramBotConfig
{
    @Bean
    @ConditionalOnProperty(name = "telegram.bot.token", matchIfMissing = false)
    public TelegramBot telegramBot( TelegramBotProperties properties )
    {
        return new TelegramBot( properties.token() );
    }
}
