package com.crypto.funding.telegram.command;

import com.crypto.funding.telegram.bot.MessageFormatter;
import com.crypto.funding.telegram.ngrok.NgrokTunnelService;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class LinksCommand
{
    private final NgrokTunnelService ngrokTunnelService;

    public LinksCommand( NgrokTunnelService ngrokTunnelService )
    {
        this.ngrokTunnelService = ngrokTunnelService;
    }

    public SendMessage build( long chatId )
    {
        Optional<NgrokTunnelService.NgrokTunnels> ngrok = ngrokTunnelService.fetchTunnels();
        String text = MessageFormatter.linksMessage( ngrok );
        InlineKeyboardMarkup keyboard = buildKeyboard( ngrok );
        return new SendMessage( chatId, text ).parseMode( ParseMode.MarkdownV2 ).replyMarkup( keyboard );
    }

    private InlineKeyboardMarkup buildKeyboard( Optional<NgrokTunnelService.NgrokTunnels> ngrok )
    {
        List<InlineKeyboardButton> urlButtons = new ArrayList<>();

        if( ngrok.isPresent() )
        {
            NgrokTunnelService.NgrokTunnels t = ngrok.get();
            if( t.monitorUrl() != null )
            {
                urlButtons.add( new InlineKeyboardButton( "🖥 Monitor UI" ).url( t.monitorUrl() ) );
            }
            if( t.grafanaUrl() != null )
            {
                urlButtons.add( new InlineKeyboardButton( "📈 Grafana" ).url( t.grafanaUrl() ) );
            }
            if( t.engineUrl() != null )
            {
                urlButtons.add( new InlineKeyboardButton( "⚙️ Engine" ).url( t.engineUrl() ) );
            }
        }

        List<InlineKeyboardButton[]> rows = new ArrayList<>();
        if( !urlButtons.isEmpty() )
        {
            rows.add( urlButtons.toArray( new InlineKeyboardButton[0] ) );
        }
        rows.add( new InlineKeyboardButton[]{ new InlineKeyboardButton( "🏠 Меню" ).callbackData( "menu:main" ) } );
        return new InlineKeyboardMarkup( rows.toArray( new InlineKeyboardButton[0][] ) );
    }
}
