package com.crypto.funding.telegram.command;

import com.crypto.funding.telegram.bot.MessageFormatter;
import com.crypto.funding.telegram.config.EnvironmentLinksProperties;
import com.crypto.funding.telegram.config.MonitorProperties;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LinksCommand
{
    private final MonitorProperties monitorProperties;
    private final EnvironmentLinksProperties envLinks;

    public LinksCommand( MonitorProperties monitorProperties, EnvironmentLinksProperties envLinks )
    {
        this.monitorProperties = monitorProperties;
        this.envLinks = envLinks;
    }

    public SendMessage build( long chatId )
    {
        String publicUrl = monitorProperties.publicUrl();
        String text = MessageFormatter.linksMessage( publicUrl, envLinks );
        InlineKeyboardMarkup keyboard = buildKeyboard( publicUrl );
        return new SendMessage( chatId, text ).parseMode( ParseMode.MarkdownV2 ).replyMarkup( keyboard );
    }

    private InlineKeyboardMarkup buildKeyboard( String publicUrl )
    {
        List<InlineKeyboardButton[]> rows = new ArrayList<>();

        if( publicUrl != null && !publicUrl.isBlank() )
        {
            rows.add( new InlineKeyboardButton[]{ new InlineKeyboardButton( "🖥 Monitor" ).url( publicUrl ) } );
        }

        EnvironmentLinksProperties.EnvLinks staging = envLinks.staging();
        if( staging != null && !staging.isEmpty() )
        {
            List<InlineKeyboardButton> stagingRow = new ArrayList<>();
            if( staging.ui() != null && !staging.ui().isBlank() )
            {
                stagingRow.add( new InlineKeyboardButton( "🖥 Monitor (staging)" ).url( staging.ui() ) );
            }
            if( staging.grafana() != null && !staging.grafana().isBlank() )
            {
                stagingRow.add( new InlineKeyboardButton( "📈 Grafana (staging)" ).url( staging.grafana() ) );
            }
            if( staging.engine() != null && !staging.engine().isBlank() )
            {
                stagingRow.add( new InlineKeyboardButton( "⚙️ Engine (staging)" ).url( staging.engine() ) );
            }
            if( !stagingRow.isEmpty() )
            {
                rows.add( stagingRow.toArray( new InlineKeyboardButton[0] ) );
            }
        }

        EnvironmentLinksProperties.EnvLinks production = envLinks.production();
        if( production != null && !production.isEmpty() )
        {
            List<InlineKeyboardButton> prodRow = new ArrayList<>();
            if( production.ui() != null && !production.ui().isBlank() )
            {
                prodRow.add( new InlineKeyboardButton( "🖥 Monitor (prod)" ).url( production.ui() ) );
            }
            if( production.grafana() != null && !production.grafana().isBlank() )
            {
                prodRow.add( new InlineKeyboardButton( "📈 Grafana (prod)" ).url( production.grafana() ) );
            }
            if( production.engine() != null && !production.engine().isBlank() )
            {
                prodRow.add( new InlineKeyboardButton( "⚙️ Engine (prod)" ).url( production.engine() ) );
            }
            if( !prodRow.isEmpty() )
            {
                rows.add( prodRow.toArray( new InlineKeyboardButton[0] ) );
            }
        }

        rows.add( new InlineKeyboardButton[]{ new InlineKeyboardButton( "🏠 Меню" ).callbackData( "menu:main" ) } );
        return new InlineKeyboardMarkup( rows.toArray( new InlineKeyboardButton[0][] ) );
    }
}
