package com.crypto.funding.telegram.command;

import com.crypto.funding.telegram.bot.MessageFormatter;
import com.crypto.funding.telegram.client.MonitorApiClient;
import com.crypto.funding.telegram.client.dto.MonitorOverview;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.stereotype.Component;

@Component
public class StatusCommand
{
    private final MonitorApiClient monitorApiClient;

    public StatusCommand( MonitorApiClient monitorApiClient )
    {
        this.monitorApiClient = monitorApiClient;
    }

    public SendMessage build( long chatId )
    {
        try
        {
            MonitorOverview overview = monitorApiClient.getOverview();
            return new SendMessage( chatId, MessageFormatter.statusMessage( overview ) )
                .parseMode( ParseMode.MarkdownV2 );
        }
        catch( Exception e )
        {
            return new SendMessage( chatId, MessageFormatter.monitorUnavailable() )
                .parseMode( ParseMode.MarkdownV2 );
        }
    }
}
