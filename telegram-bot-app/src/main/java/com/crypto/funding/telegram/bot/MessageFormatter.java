package com.crypto.funding.telegram.bot;

import com.crypto.funding.telegram.client.dto.ArmedTradeSummary;
import com.crypto.funding.telegram.client.dto.CandidateSummary;
import com.crypto.funding.telegram.client.dto.MonitorOverview;
import com.crypto.funding.telegram.ngrok.NgrokTunnelService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public final class MessageFormatter
{
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter
        .ofPattern( "dd.MM.yyyy HH:mm" )
        .withZone( ZoneOffset.UTC );

    private MessageFormatter()
    {
    }

    public static String welcome( String firstName )
    {
        return """
            👋 *Привет, %s\\!*

            Я — ассистент системы мониторинга крипто\\-фандинга\\.
            Помогу разобраться в системе, дам актуальные ссылки и уведомлю о новых сигналах\\.

            ━━━━━━━━━━━━━━━━━━━━
            📚 *Что я умею:*

            `/faq` — справочник по всем компонентам системы
            `/links` — актуальные ссылки на UI, Grafana, Engine
            `/status` — текущее состояние системы
            `/signals` — последние 5 сигналов к фандингу
            `/help` — список всех команд

            ━━━━━━━━━━━━━━━━━━━━
            💡 _Уведомления о новых сигналах приходят автоматически\\._
            """.formatted( escapeMarkdown( firstName ) );
    }

    public static String help()
    {
        return """
            📖 *Список команд*

            ━━━━━━━━━━━━━━━━━━━━
            🏠 `/start` — главное меню
            📚 `/faq` — справочник по системе
            🔗 `/links` — ссылки на окружения
            📊 `/status` — состояние монитора
            📡 `/signals` — последние сигналы
            ℹ️ `/help` — эта справка
            """;
    }

    public static String faqMenu()
    {
        return """
            📚 *Справочник по системе*

            ━━━━━━━━━━━━━━━━━━━━
            Выбери раздел для изучения:
            """;
    }

    public static String linksMessage( Optional<NgrokTunnelService.NgrokTunnels> ngrok )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "🔗 *Актуальные ссылки*\n\n" );
        sb.append( "━━━━━━━━━━━━━━━━━━━━\n" );
        sb.append( "🟡 *Staging \\(Mac Mini \\+ ngrok\\)*\n" );
        if( ngrok.isPresent() )
        {
            sb.append( "_🔄 Ссылки получены из ngrok — кнопки ниже актуальны_\n" );
        }
        else
        {
            sb.append( "_ngrok недоступен или туннель не запущен_\n" );
        }
        sb.append( "\n━━━━━━━━━━━━━━━━━━━━\n" );
        sb.append( "🟢 *Production*\n" );
        sb.append( "_Не настроено_\n" );
        return sb.toString();
    }

    public static String statusMessage( MonitorOverview overview )
    {
        String modeEmoji = "testnet".equalsIgnoreCase( overview.globalAccessMode() ) ? "🧪" : "🔴";
        StringBuilder sb = new StringBuilder();
        sb.append( "📊 *Состояние системы*\n\n" );
        sb.append( "━━━━━━━━━━━━━━━━━━━━\n" );
        sb.append( String.format( "🕐 *Снимок:* %s UTC\n", fmt( overview.generatedAt() ) ) );
        sb.append( String.format( "🔖 *Версия:* `%s`\n", escapeMarkdown( overview.version() ) ) );
        sb.append( String.format( "%s *Режим:* `%s`%s\n",
            modeEmoji,
            escapeMarkdown( overview.globalAccessMode() ),
            overview.globalModeOverridden() ? " ⚠️ _override_" : "" ) );
        sb.append( "\n━━━━━━━━━━━━━━━━━━━━\n" );
        sb.append( "📋 *Пайплайн:*\n" );
        sb.append( String.format( "  📡 Кандидаты на рассмотрении: *%d*\n", overview.pendingCandidates() ) );
        sb.append( String.format( "  📋 Активных событий: *%d*\n", overview.fundingEvents() ) );
        sb.append( String.format( "  🔍 Новых событий: *%d*\n", overview.discoveredEvents() ) );
        sb.append( String.format( "  🎯 Вооружённых трейдов: *%d*\n", overview.armedTrades() ) );
        sb.append( "\n━━━━━━━━━━━━━━━━━━━━\n" );
        sb.append( String.format( "🏦 *Активных бирж: %d*\n", overview.activeVenues() ) );

        if( overview.venues() != null && !overview.venues().isEmpty() )
        {
            for( var venue : overview.venues() )
            {
                String statusEmoji = "OK".equalsIgnoreCase( venue.connectionStatus() ) ? "✅" : "❌";
                sb.append( String.format( "  %s `%s` — %s\n",
                    statusEmoji,
                    escapeMarkdown( venue.venue() ),
                    escapeMarkdown( venue.mode() != null ? venue.mode() : "—" ) ) );
            }
        }

        return sb.toString();
    }

    public static String signalsMessage( List<CandidateSummary> candidates )
    {
        if( candidates.isEmpty() )
        {
            return "📡 *Последние сигналы*\n\n_Сигналов пока нет\\._";
        }

        StringBuilder sb = new StringBuilder();
        sb.append( "📡 *Последние сигналы к фандингу*\n\n" );
        sb.append( "━━━━━━━━━━━━━━━━━━━━\n" );

        for( CandidateSummary c : candidates )
        {
            sb.append( formatCandidateRow( c ) );
            sb.append( "\n" );
        }

        return sb.toString();
    }

    public static String newTradeAlert( ArmedTradeSummary trade, String monitorUiUrl )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "🎯 *Новый вооружённый трейд*\n\n" );
        sb.append( "━━━━━━━━━━━━━━━━━━━━\n" );
        sb.append( String.format( "📌 *Символ:* `%s`\n", escapeMarkdown( trade.displaySymbol() ) ) );
        sb.append( String.format( "🏦 *Биржа:* %s\n", escapeMarkdown( trade.venue() ) ) );
        if( trade.intendedSide() != null )
        {
            sb.append( String.format( "📉 *Направление:* `%s`\n", escapeMarkdown( trade.intendedSide() ) ) );
        }
        if( trade.notionalUsd() != null )
        {
            sb.append( String.format( "💵 *Объём:* `%s USD`\n",
                escapeMarkdown( String.format( "%.2f", trade.notionalUsd() ) ) ) );
        }
        if( trade.plannedEntryAt() != null )
        {
            sb.append( String.format( "⏰ *Вход:* `%s UTC`\n",
                escapeMarkdown( fmt( trade.plannedEntryAt() ) ) ) );
        }
        sb.append( String.format( "🔖 *Статус:* `%s`\n", escapeMarkdown( trade.state() ) ) );

        if( monitorUiUrl != null && !monitorUiUrl.isBlank() )
        {
            sb.append( "\n" );
            sb.append( String.format( "[🖥 Открыть монитор](%s)", monitorUiUrl ) );
        }

        return sb.toString();
    }

    public static String newSignalAlert( CandidateSummary candidate, String monitorUiUrl )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "🚨 *Новый сигнал к фандингу*\n\n" );
        sb.append( "━━━━━━━━━━━━━━━━━━━━\n" );
        sb.append( String.format( "📌 *Символ:* `%s`\n", escapeMarkdown( candidate.displaySymbol() ) ) );
        sb.append( String.format( "🏦 *Биржа:* %s\n", escapeMarkdown( candidate.displayVenue() ) ) );

        if( candidate.sourceFundingRatePct() != null )
        {
            sb.append( String.format( "💰 *Ставка:* `%s%%`\n",
                escapeMarkdown( formatRate( candidate.sourceFundingRatePct() ) ) ) );
        }
        if( candidate.sourceFundingTime() != null )
        {
            sb.append( String.format( "⏰ *Фандинг:* `%s UTC`\n",
                escapeMarkdown( fmt( candidate.sourceFundingTime() ) ) ) );
        }
        sb.append( String.format( "🕐 *Обнаружен:* `%s UTC`\n",
            escapeMarkdown( fmt( candidate.detectedAt() ) ) ) );
        sb.append( String.format( "🔖 *Статус:* `%s`\n", escapeMarkdown( candidate.status() ) ) );

        if( monitorUiUrl != null && !monitorUiUrl.isBlank() )
        {
            sb.append( "\n" );
            sb.append( String.format( "[🖥 Открыть монитор](%s)", monitorUiUrl ) );
        }

        return sb.toString();
    }

    public static String accessDenied()
    {
        return "🚫 *Доступ запрещён*\n\n_У тебя нет прав для использования этого бота\\._";
    }

    public static String monitorUnavailable()
    {
        return "⚠️ *Монитор недоступен*\n\n_Не удалось получить данные от монитора\\. Проверь соединение\\._";
    }

    private static String formatCandidateRow( CandidateSummary c )
    {
        String statusEmoji = switch( c.status() != null ? c.status() : "" )
        {
            case "NORMALIZED" -> "✅";
            case "PENDING" -> "🕐";
            case "FAILED" -> "❌";
            case "EVENT_CREATED" -> "📋";
            case "REJECTED" -> "🚫";
            default -> "•";
        };

        StringBuilder sb = new StringBuilder();
        sb.append( String.format( "%s `%s`", statusEmoji, escapeMarkdown( c.displaySymbol() ) ) );
        if( !c.displayVenue().equals( "—" ) )
        {
            sb.append( String.format( " — %s", escapeMarkdown( c.displayVenue() ) ) );
        }
        if( c.sourceFundingRatePct() != null )
        {
            sb.append( String.format( " `%s%%`", escapeMarkdown( formatRate( c.sourceFundingRatePct() ) ) ) );
        }
        sb.append( "\n" );
        sb.append( String.format( "   🕐 %s UTC\n", fmt( c.detectedAt() ) ) );
        return sb.toString();
    }

    private static void appendLink( StringBuilder sb, String label, String url )
    {
        if( url != null && !url.isBlank() )
        {
            sb.append( String.format( "  [%s](%s)\n", label, url ) );
        }
        else
        {
            sb.append( String.format( "  %s: _не настроено_\n", label ) );
        }
    }

    private static String fmt( Instant instant )
    {
        if( instant == null )
        {
            return "—";
        }
        return DATETIME_FMT.format( instant );
    }

    private static String formatRate( BigDecimal rate )
    {
        if( rate == null )
        {
            return "—";
        }
        return String.format( "%+.4f", rate );
    }

    public static String escapeMarkdown( String text )
    {
        if( text == null )
        {
            return "";
        }
        return text
            .replace( "\\", "\\\\" )
            .replace( "_", "\\_" )
            .replace( "*", "\\*" )
            .replace( "[", "\\[" )
            .replace( "]", "\\]" )
            .replace( "(", "\\(" )
            .replace( ")", "\\)" )
            .replace( "~", "\\~" )
            .replace( "`", "\\`" )
            .replace( ">", "\\>" )
            .replace( "#", "\\#" )
            .replace( "+", "\\+" )
            .replace( "-", "\\-" )
            .replace( "=", "\\=" )
            .replace( "|", "\\|" )
            .replace( "{", "\\{" )
            .replace( "}", "\\}" )
            .replace( ".", "\\." )
            .replace( "!", "\\!" );
    }
}
