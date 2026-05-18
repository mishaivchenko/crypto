package com.crypto.funding.telegram.command;

import com.crypto.funding.telegram.bot.MessageFormatter;
import com.crypto.funding.telegram.config.EnvironmentLinksProperties;
import com.crypto.funding.telegram.ngrok.NgrokTunnelService;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class LinksCommand
{
    private final EnvironmentLinksProperties linksProperties;
    private final NgrokTunnelService ngrokTunnelService;

    public LinksCommand( EnvironmentLinksProperties linksProperties, NgrokTunnelService ngrokTunnelService )
    {
        this.linksProperties = linksProperties;
        this.ngrokTunnelService = ngrokTunnelService;
    }

    public SendMessage build( long chatId )
    {
        Optional<NgrokTunnelService.NgrokTunnels> ngrok = ngrokTunnelService.fetchTunnels();
        String text = MessageFormatter.linksMessage( linksProperties, ngrok );
        InlineKeyboardMarkup keyboard = buildQuickAccessKeyboard( ngrok );
        SendMessage msg = new SendMessage( chatId, text ).parseMode( ParseMode.MarkdownV2 );
        if( keyboard != null )
        {
            msg.replyMarkup( keyboard );
        }
        return msg;
    }

    private InlineKeyboardMarkup buildQuickAccessKeyboard( Optional<NgrokTunnelService.NgrokTunnels> ngrok )
    {
        List<InlineKeyboardButton> buttons = new ArrayList<>();

        if( ngrok.isPresent() )
        {
            NgrokTunnelService.NgrokTunnels t = ngrok.get();
            if( t.monitorUrl() != null )
            {
                buttons.add( new InlineKeyboardButton( "🖥 Monitor UI" ).url( t.monitorUrl() ) );
            }
            if( t.grafanaUrl() != null )
            {
                buttons.add( new InlineKeyboardButton( "📈 Grafana" ).url( t.grafanaUrl() ) );
            }
        }
        else
        {
            var staging = linksProperties.staging();
            if( staging != null )
            {
                if( staging.ui() != null && !staging.ui().isBlank() )
                {
                    buttons.add( new InlineKeyboardButton( "🖥 Staging UI" ).url( staging.ui() ) );
                }
                if( staging.grafana() != null && !staging.grafana().isBlank() )
                {
                    buttons.add( new InlineKeyboardButton( "📈 Grafana" ).url( staging.grafana() ) );
                }
            }
        }

        if( buttons.isEmpty() )
        {
            return null;
        }
        return new InlineKeyboardMarkup( buttons.toArray( new InlineKeyboardButton[0] ) );
    }
}
