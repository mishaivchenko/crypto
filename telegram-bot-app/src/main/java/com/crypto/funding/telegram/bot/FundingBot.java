package com.crypto.funding.telegram.bot;

import com.crypto.funding.telegram.config.TelegramBotProperties;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "telegram.bot.token", matchIfMissing = false)
public class FundingBot
{
    private static final Logger log = LoggerFactory.getLogger( FundingBot.class );

    private final TelegramBot bot;
    private final TelegramBotProperties properties;
    private final CommandRouter commandRouter;

    public FundingBot( TelegramBot bot, TelegramBotProperties properties, CommandRouter commandRouter )
    {
        this.bot = bot;
        this.properties = properties;
        this.commandRouter = commandRouter;
    }

    @PostConstruct
    public void startPolling()
    {
        log.info( "Starting Telegram bot long polling" );
        bot.setUpdatesListener( updates -> {
            for( Update update : updates )
            {
                handleUpdate( update );
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        }, e -> log.error( "Polling error", e ) );
    }

    public void sendAlert( long chatId, String markdownText )
    {
        SendMessage msg = new SendMessage( chatId, markdownText ).parseMode( ParseMode.MarkdownV2 );
        execute( msg );
    }

    private void handleUpdate( Update update )
    {
        try
        {
            List<BaseRequest<?, ?>> responses;
            if( update.message() != null && update.message().text() != null )
            {
                responses = commandRouter.routeMessage( update.message() );
            }
            else if( update.callbackQuery() != null )
            {
                responses = commandRouter.routeCallback( update.callbackQuery() );
            }
            else
            {
                return;
            }
            responses.forEach( this::execute );
        }
        catch( Exception e )
        {
            log.error( "Error handling update {}", update.updateId(), e );
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void execute( BaseRequest<?, ?> request )
    {
        var response = bot.execute( (BaseRequest) request );
        if( !response.isOk() )
        {
            log.warn( "Telegram API error {}: {}", response.errorCode(), response.description() );
        }
    }
}
