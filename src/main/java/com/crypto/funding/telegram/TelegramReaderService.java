package com.crypto.funding.telegram;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelegramReaderService
{
    private final SimpleTelegramClient client;
    private final ConcurrentHashMap<Long, Consumer<String>> listeners = new ConcurrentHashMap<>();

    private static final Pattern P_TICKERS = Pattern.compile(
        "(?i)\\b([A-Z0-9]{2,15})\\s*[/\\-_]?\\s*(USDT|USDC|BTC|ETH)(?::\\2)?\\b"
    );

    public TelegramReaderService( TelegramLoginService login )
    {
        this.client = login.client();

        client.addUpdateHandler( TdApi.UpdateNewMessage.class, upd -> {
            String text = normalize( extractText( upd.message ) );
            Consumer<String> cb = listeners.get( upd.message.chatId );
            if( cb != null && text != null && !text.isBlank() )
            {
                cb.accept( text );
            }
        } );
    }

    /** Разрешить @username/ссылку → chatId и гарантировать, что мы участники. */
    public Optional<Long> resolveAndEnsureJoin(String usernameOrLink) {
        try {
            // invite-link?
            if (usernameOrLink.contains("+")) {
                String link = usernameOrLink.startsWith("https://t.me/")
                              ? usernameOrLink : ("https://t.me/" + usernameOrLink.replace("t.me/",""));
                client.send(new TdApi.JoinChatByInviteLink(link)).join();
                // get info back
                TdApi.ChatInviteLinkInfo info = client.send(new TdApi.CheckChatInviteLink(link)).join();
                return Optional.of(info.chatId);
            }
            // public @username
            String uname = usernameOrLink.replace("https://t.me/","").replace("@","").trim();
          //  TdApi.Chat chat = client.send(new TdApi.SearchPublicChat(uname)).join();
            TdApi.Chat chat = client.send( new TdApi.GetChat( -5031477415L ) ).join();
            // try to join (если уже участник — TDLib вернёт ошибку, игнорируем)
            try { client.send(new TdApi.JoinChat(chat.id)).join(); } catch (Exception ignored) {}
            return Optional.of(chat.id);
        } catch (Exception e) {
            System.out.println("[TG] resolve/join failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Подписать callback на новые сообщения конкретного чата. */
    public void onNewMessages(long chatId, Consumer<String> handler) {
        listeners.put(chatId, handler);
    }
//
//    /** Вытащить тикеры из произвольного текста/капшена. Нормализация к BASE/QUOTE. */
//    public static Set<String> extractTickers(String text) {
//        if (text == null || text.isBlank()) return Set.of();
//        var s = text.toUpperCase( Locale.ROOT);
//        var m = P_TICKERS.matcher(s);
//        var out = new LinkedHashSet<String>();
//        while (m.find()) {
//            var base = m.group(1).replaceAll("[^A-Z0-9]","");
//            var quote = m.group(2);
//            if (base.length() >= 2 && base.length() <= 15) {
//                out.add(base + "/" + quote);
//            }
//        }
//        return out;
//    }

    public static Set<String> extractSymbolsFromMessage(String text) {
        if (text == null || text.isBlank()) return Set.of();
        var out = new java.util.LinkedHashSet<String>();
        var s = text.strip();

        // 1) funding-блоки: строки coin: ...
        var coinLine = java.util.regex.Pattern.compile("(?im)^\\s*coin\\s*:\\s*([^\\n]+)");
        var m1 = coinLine.matcher(s);
        while (m1.find()) {
            var raw = m1.group(1).trim();
            var norm = normalizeSymbol(raw);
            if (norm != null) out.add(norm);
        }

        // 2) AB RATES/PRICES: Монеты:/Coins:
        var coinsList = java.util.regex.Pattern.compile("(?is)\\b(монет[аы]|coins?)\\s*:\\s*([A-Z0-9,\\s/_:+-]+)");
        var m2 = coinsList.matcher(s);
        while (m2.find()) {
            var list = m2.group(2);
            for (var part : list.split("[,\\s]+")) {
                var p = part.trim();
                if (p.isEmpty()) continue;
                var norm = normalizeSymbol(p);
                if (norm != null) out.add(norm);
            }
        }

        return out;
    }

    // Нормализация: COAIUSDT / COAI/USDT:USDT / COAI-USDT → COAI/USDT
    private static String normalizeSymbol(String raw) {
        if (raw == null) return null;
        var s = raw.trim().toUpperCase();
        // обрежем всё после двоеточия (часто ":USDT")
        int colon = s.indexOf(':');
        if (colon > 0) s = s.substring(0, colon);

        s = s.replace('-', '/').replace('_', '/').replaceAll("/+", "/");

        // если формат был слитный COINUSDT / COINUSDC / COINBTC / COINETH
        var m = java.util.regex.Pattern.compile("^([A-Z0-9]{2,15})(USDT|USDC|BTC|ETH)$").matcher(s);
        if (m.find()) s = m.group(1) + "/" + m.group(2);

        // если совсем без квоты — добавим /USDT (для списков типа "SD,")
        if (!s.contains("/")) s = s + "/USDT";

        // базовый sanity-check (отсечь мусор вроде "5M/USDT")
        if (!s.matches("^[A-Z][A-Z0-9]{1,14}/(USDT|USDC|BTC|ETH)$")) return null;

        return s;
    }

    public Optional<Long> resolveChatId( String usernameOrLink )
    {
        try
        {
            String uname = usernameOrLink.replace( "https://t.me/", "" ).replace( "@", "" ).trim();
            TdApi.Chat chat = client.send( new TdApi.SearchPublicChat( uname ) ).join();
            return Optional.of( chat.id );
        }
        catch( Exception e )
        {
            return Optional.empty();
        }
    }

    public String readLastMessage( long chatId, int count, String type )
    {
        String lastMessage = null;
        try
        {

            TdApi.Messages msgs = client.send( new TdApi.GetChatHistory( chatId, 0, 0, count == 0 ? 1 : count, false ) ).join();

//            ArbSignalParser.FundingSignal fundingSignal = arbSignalParser.parseAll( extractText( msgs.messages[0] ) ).fundings().get( 0 );
//            String parsedText = fundingSignal.symbol() + " " + fundingSignal.exchange() + " " + fundingSignal.timeToFmins();

            lastMessage =
                Arrays.stream( msgs.messages )
                      .sorted( ( m1, m2 ) -> Long.compare( m2.date, m1.date ) ) // сортировка по дате (новые первыми)
                      .map( TelegramReaderService::extractText )
                      .map( TelegramReaderService::normalize )
                      .filter( text -> text.contains( type ) )
                      .findFirst().orElse( "" );

        }
        catch( Exception ignored )
        {

        }
        return lastMessage;
    }

    // telegram/TelegramReaderService.java

    private static String extractText( TdApi.Message m )
    {
        if( m == null || m.content == null )
        {
            return null;
        }
        TdApi.MessageContent c = m.content;

        // helper: вытянуть строку из FormattedText
        java.util.function.Function<TdApi.FormattedText, String> ft = t -> ( t == null ) ? null : t.text;

        if( c instanceof TdApi.MessageText t )
        {
            return ft.apply( t.text );
        }
        if( c instanceof TdApi.MessagePhoto p )
        {
            return ft.apply( p.caption );
        }
        if( c instanceof TdApi.MessageVideo v )
        {
            return ft.apply( v.caption );
        }
        if( c instanceof TdApi.MessageDocument d )
        {
            return ft.apply( d.caption );
        }
        if( c instanceof TdApi.MessageAnimation a )
        {
            return ft.apply( a.caption );
        }
        if( c instanceof TdApi.MessageAudio a )
        {
            return ft.apply( a.caption );
        }
        if( c instanceof TdApi.MessageVoiceNote v )
        {
            return ft.apply( v.caption );
        }
//        if (c instanceof TdApi.MessageVideoNote v)      return ft.apply(v.caption);
//        if (c instanceof TdApi.MessageSticker s)        return s.emoji;                      // эмодзи стикера
        if( c instanceof TdApi.MessagePoll p )
        {
            return p.poll.question;              // вопрос опроса
        }
        if( c instanceof TdApi.MessageLocation l )
        {
            return "LOCATION %f,%f"
                .formatted( l.location.latitude, l.location.longitude );
        }
        if( c instanceof TdApi.MessageVenue v )
        {
            return v.venue.title;
        }
        if( c instanceof TdApi.MessageContact ct )
        {
            return ct.contact.firstName + " " + ct.contact.lastName;
        }

        // fallback: ничего не извлекли
        return null;
    }

//    private static String normalize( String s )
//    {
//        if( s == null )
//        {
//            return null;
//        }
//        return s.strip().replaceAll( "\\s+", " " );
//    }

    private static String normalize( String s )
    {
        return s == null ? null : s.strip()
                                   .replace( '\u00A0', ' ' )     // non-breaking space
                                   .replaceAll( "[ \\t\\x0B\\f\\r]+", " " )
                                   .replaceAll( "\\n{3,}", "\n\n" );
    }

}
