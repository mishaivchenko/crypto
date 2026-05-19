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
    private final NgrokTunnelService ngrokTunnelService;
    private final EnvironmentLinksProperties envLinks;

    public LinksCommand( NgrokTunnelService ngrokTunnelService, EnvironmentLinksProperties envLinks )
    {
        this.ngrokTunnelService = ngrokTunnelService;
        this.envLinks = envLinks;
    }

    public SendMessage build( long chatId )
    {
        Optional<NgrokTunnelService.NgrokTunnels> ngrok = ngrokTunnelService.fetchTunnels();
        String text = MessageFormatter.linksMessage( ngrok, envLinks );
        InlineKeyboardMarkup keyboard = buildKeyboard( ngrok );
        return new SendMessage( chatId, text ).parseMode( ParseMode.MarkdownV2 ).replyMarkup( keyboard );
    }

    private InlineKeyboardMarkup buildKeyboard( Optional<NgrokTunnelService.NgrokTunnels> ngrok )
    {
        List<InlineKeyboardButton[]> rows = new ArrayList<>();

        if( ngrok.isPresent() )
        {
            NgrokTunnelService.NgrokTunnels t = ngrok.get();
            List<InlineKeyboardButton> ngrokRow = new ArrayList<>();
            if( t.monitorUrl() != null )
            {
                ngrokRow.add( new InlineKeyboardButton( "🖥 Monitor" ).url( t.monitorUrl() ) );
            }
            if( t.grafanaUrl() != null )
            {
                ngrokRow.add( new InlineKeyboardButton( "📈 Grafana" ).url( t.grafanaUrl() ) );
            }
            if( t.engineUrl() != null )
            {
                ngrokRow.add( new InlineKeyboardButton( "⚙️ Engine" ).url( t.engineUrl() ) );
            }
            if( !ngrokRow.isEmpty() )
            {
                rows.add( ngrokRow.toArray( new InlineKeyboardButton[0] ) );
            }
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
