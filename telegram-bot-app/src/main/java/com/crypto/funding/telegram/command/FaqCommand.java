package com.crypto.funding.telegram.command;

import com.crypto.funding.telegram.bot.MessageFormatter;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class FaqCommand
{
    private static final Map<String, String> TOPICS = Map.of(
        "faq:overview", "📐 Архитектура",
        "faq:signals", "📡 Сигналы",
        "faq:events", "📋 События",
        "faq:trades", "📊 Трейды",
        "faq:venues", "🏦 Биржи",
        "faq:engine", "⚙️ Engine",
        "faq:settings", "🔧 Настройки",
        "faq:latency", "⏱ Латентность"
    );

    private static final String[][] KEYBOARD_LAYOUT = {
        { "faq:overview", "faq:signals" },
        { "faq:events", "faq:trades" },
        { "faq:venues", "faq:engine" },
        { "faq:settings", "faq:latency" }
    };

    public SendMessage buildMenu( long chatId )
    {
        return new SendMessage( chatId, MessageFormatter.faqMenu() )
            .parseMode( ParseMode.MarkdownV2 )
            .replyMarkup( buildKeyboard() );
    }

    public SendMessage buildTopicResponse( long chatId, String callbackData )
    {
        String topic = callbackData.replace( "faq:", "" );
        String content = loadTopic( topic );
        return new SendMessage( chatId, content )
            .parseMode( ParseMode.MarkdownV2 )
            .replyMarkup( buildBackKeyboard() );
    }

    public boolean handles( String callbackData )
    {
        return callbackData != null && callbackData.startsWith( "faq:" );
    }

    private InlineKeyboardMarkup buildKeyboard()
    {
        InlineKeyboardButton[][] buttons = new InlineKeyboardButton[KEYBOARD_LAYOUT.length][];
        for( int row = 0; row < KEYBOARD_LAYOUT.length; row++ )
        {
            String[] rowKeys = KEYBOARD_LAYOUT[row];
            buttons[row] = new InlineKeyboardButton[rowKeys.length];
            for( int col = 0; col < rowKeys.length; col++ )
            {
                String key = rowKeys[col];
                String label = TOPICS.getOrDefault( key, key );
                buttons[row][col] = new InlineKeyboardButton( label ).callbackData( key );
            }
        }
        return new InlineKeyboardMarkup( buttons );
    }

    private InlineKeyboardMarkup buildBackKeyboard()
    {
        return new InlineKeyboardMarkup(
            new InlineKeyboardButton( "◀️ Назад к FAQ" ).callbackData( "faq:menu" )
        );
    }

    private String loadTopic( String topic )
    {
        try
        {
            ClassPathResource resource = new ClassPathResource( "faq/" + topic + ".md" );
            return resource.getContentAsString( StandardCharsets.UTF_8 );
        }
        catch( IOException e )
        {
            return "❓ *Раздел не найден*\n\n_Попробуй другой раздел\\._";
        }
    }
}
