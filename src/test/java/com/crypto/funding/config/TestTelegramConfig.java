package com.crypto.funding.config;

import com.crypto.funding.telegram.TelegramLoginService;
import it.tdlight.client.SimpleTelegramClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestTelegramConfig
{
    @Bean
    public TelegramLoginService telegramLoginService()
    {
        TelegramLoginService login = Mockito.mock( TelegramLoginService.class );
        SimpleTelegramClient client = Mockito.mock( SimpleTelegramClient.class );
        Mockito.when( login.client() ).thenReturn( client );
        return login;
    }
}
