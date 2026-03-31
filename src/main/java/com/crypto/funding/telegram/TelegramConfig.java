package com.crypto.funding.telegram;

import com.crypto.funding.api.TelegramBot;
import it.tdlight.client.APIToken;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.nio.file.Path;

@Configuration
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelegramConfig
{
    @Bean
    public TDLibSettings tdLibSettings(
        @Value( "${telegram.apiId}" ) int apiId,
        @Value( "${telegram.apiHash}" ) String apiHash,
        @Value( "${telegram.sessionDir:./data/tdlib}" ) String sessionDir )
    {
        System.out.println( "Environment variables:" + apiId + " " + apiHash + " " + sessionDir );
        TDLibSettings s = TDLibSettings.create( new APIToken( apiId, apiHash ) );
        s.setDatabaseDirectoryPath( Path.of( sessionDir, "db" ) );
        s.setDownloadedFilesDirectoryPath( Path.of( sessionDir, "files" ) );
        s.setDeviceModel( "Java21-Arb" );
        s.setApplicationVersion( "0.1.0" );
        return s;
    }

    @Bean( destroyMethod = "close" )
    public SimpleTelegramClientFactory telegramClient()
    {
        return new SimpleTelegramClientFactory();
    }

    @Bean
    @ConditionalOnProperty(prefix = "telegram.bot", name = "enabled", havingValue = "true")
    public TelegramBotsApi telegramBotsApi( TelegramBot telegramBot )
    {
        TelegramBotsApi telegramBotsApi;
        try
        {
            telegramBotsApi = new TelegramBotsApi( DefaultBotSession.class);
            telegramBotsApi.registerBot( telegramBot );
        }
        catch( TelegramApiException e )
        {
            throw new RuntimeException( e );
        }

        return telegramBotsApi;
    }

}
