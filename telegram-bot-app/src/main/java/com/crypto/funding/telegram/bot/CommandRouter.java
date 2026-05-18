package com.crypto.funding.telegram.bot;

import com.crypto.funding.telegram.command.FaqCommand;
import com.crypto.funding.telegram.command.LinksCommand;
import com.crypto.funding.telegram.command.SignalsCommand;
import com.crypto.funding.telegram.command.StatusCommand;
import com.crypto.funding.telegram.config.TelegramBotProperties;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CommandRouter
{
    private final TelegramBotProperties botProperties;
    private final FaqCommand faqCommand;
    private final LinksCommand linksCommand;
    private final StatusCommand statusCommand;
    private final SignalsCommand signalsCommand;

    public CommandRouter(
        TelegramBotProperties botProperties,
        FaqCommand faqCommand,
        LinksCommand linksCommand,
        StatusCommand statusCommand,
        SignalsCommand signalsCommand
    )
    {
        this.botProperties = botProperties;
        this.faqCommand = faqCommand;
        this.linksCommand = linksCommand;
        this.statusCommand = statusCommand;
        this.signalsCommand = signalsCommand;
    }

    public List<BaseRequest<?, ?>> routeMessage( Message message )
    {
        long chatId = message.chat().id();
        long userId = message.from().id();

        if( isAccessDenied( userId ) )
        {
            return List.of( new SendMessage( chatId, MessageFormatter.accessDenied() )
                .parseMode( ParseMode.MarkdownV2 ) );
        }

        String text = message.text();
        if( text == null )
        {
            return List.of();
        }

        String command = text.split( " " )[0].toLowerCase();
        return switch( command )
        {
            case "/start" -> List.of( buildWelcome( chatId, message.from().firstName() ) );
            case "/help" -> List.of( new SendMessage( chatId, MessageFormatter.help() )
                .parseMode( ParseMode.MarkdownV2 ).replyMarkup( buildMainKeyboard() ) );
            case "/faq" -> List.of( faqCommand.buildMenu( chatId ) );
            case "/links" -> List.of( linksCommand.build( chatId ) );
            case "/status" -> List.of( statusCommand.build( chatId ) );
            case "/signals" -> List.of( signalsCommand.build( chatId ) );
            default -> List.of();
        };
    }

    public List<BaseRequest<?, ?>> routeCallback( CallbackQuery callbackQuery )
    {
        MaybeInaccessibleMessage maybeMsg = callbackQuery.maybeInaccessibleMessage();
        long chatId = maybeMsg.chat().id();
        long userId = callbackQuery.from().id();
        String data = callbackQuery.data();

        AnswerCallbackQuery ack = new AnswerCallbackQuery( callbackQuery.id() );

        if( isAccessDenied( userId ) )
        {
            return List.of( ack, new SendMessage( chatId, MessageFormatter.accessDenied() )
                .parseMode( ParseMode.MarkdownV2 ) );
        }

        if( "menu:main".equals( data ) )
        {
            return List.of( ack, buildWelcome( chatId, callbackQuery.from().firstName() ) );
        }

        if( "menu:faq".equals( data ) )
        {
            return List.of( ack, faqCommand.buildMenu( chatId ) );
        }

        if( "menu:links".equals( data ) )
        {
            return List.of( ack, linksCommand.build( chatId ) );
        }

        if( "menu:status".equals( data ) )
        {
            return List.of( ack, statusCommand.build( chatId ) );
        }

        if( "menu:signals".equals( data ) )
        {
            return List.of( ack, signalsCommand.build( chatId ) );
        }

        if( "faq:menu".equals( data ) )
        {
            return List.of( ack, faqCommand.buildMenu( chatId ) );
        }

        if( faqCommand.handles( data ) )
        {
            return List.of( ack, faqCommand.buildTopicResponse( chatId, data ) );
        }

        return List.of( ack );
    }

    private SendMessage buildWelcome( long chatId, String firstName )
    {
        return new SendMessage( chatId, MessageFormatter.welcome( firstName ) )
            .parseMode( ParseMode.MarkdownV2 )
            .replyMarkup( buildMainKeyboard() );
    }

    private InlineKeyboardMarkup buildMainKeyboard()
    {
        return new InlineKeyboardMarkup(
            new InlineKeyboardButton[]{ new InlineKeyboardButton( "📚 FAQ" ).callbackData( "menu:faq" ),
                new InlineKeyboardButton( "🔗 Ссылки" ).callbackData( "menu:links" ) },
            new InlineKeyboardButton[]{ new InlineKeyboardButton( "📊 Статус" ).callbackData( "menu:status" ),
                new InlineKeyboardButton( "📡 Сигналы" ).callbackData( "menu:signals" ) }
        );
    }

    private boolean isAccessDenied( long userId )
    {
        List<Long> allowed = botProperties.allowedUserIdList();
        return !allowed.isEmpty() && !allowed.contains( userId );
    }
}
