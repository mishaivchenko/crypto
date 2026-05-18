package com.crypto.funding.telegram.command;

import com.crypto.funding.telegram.bot.MessageFormatter;
import com.crypto.funding.telegram.client.MonitorApiClient;
import com.crypto.funding.telegram.client.PageResponse;
import com.crypto.funding.telegram.client.dto.CandidateSummary;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SignalsCommand
{
    private final MonitorApiClient monitorApiClient;

    public SignalsCommand( MonitorApiClient monitorApiClient )
    {
        this.monitorApiClient = monitorApiClient;
    }

    public SendMessage build( long chatId )
    {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
            new InlineKeyboardButton[]{ new InlineKeyboardButton( "🔄 Обновить" ).callbackData( "menu:signals" ),
                new InlineKeyboardButton( "🏠 Меню" ).callbackData( "menu:main" ) }
        );
        try
        {
            PageResponse<CandidateSummary> page = monitorApiClient.getCandidates( null, 5, 0 );
            List<CandidateSummary> candidates = page.content() != null ? page.content() : List.of();
            return new SendMessage( chatId, MessageFormatter.signalsMessage( candidates ) )
                .parseMode( ParseMode.MarkdownV2 )
                .replyMarkup( keyboard );
        }
        catch( Exception e )
        {
            return new SendMessage( chatId, MessageFormatter.monitorUnavailable() )
                .parseMode( ParseMode.MarkdownV2 )
                .replyMarkup( keyboard );
        }
    }
}
