package com.crypto.funding.api;

import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.service.ApprovedFundingStore;
import com.crypto.funding.persistence.service.FundingApprovalService;
import com.crypto.funding.telegram.TelegramSessionStore;
import com.crypto.funding.trading.TestOrderEngine;
import com.crypto.funding.watchlist.ArbitrageWatchlistService;
import com.crypto.funding.watchlist.FundingRefresherService;
import com.crypto.funding.watchlist.FundingWatchlistService;
import com.crypto.funding.watchlist.FundingWatchlistService.Item;
import com.crypto.funding.watchlist.FundingWatchlistService.WatchFunding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class TelegramBot extends TelegramLongPollingBot
{

    private static final Logger log = LoggerFactory.getLogger( TelegramBot.class );

    // ---- callback constants ----
    private static final String CB_NAV_MENU = "NAV:MENU";
    private static final String CB_NAV_FUNDING = "NAV:FUNDING";
    private static final String CB_NAV_FUNDING_APPROVED = "NAV:FUNDING_APPROVED";
    private static final String CB_NAV_ARB = "NAV:ARB";
    private static final String CB_CANCEL = "CANCEL";

    // Funding approve flow
    private static final String CB_FUND_APPROVE = "FUND:APPROVE:";           // FUND:APPROVE:<symbol>
    private static final String CB_FUND_UNAPPROVE = "FUND:UNAPPROVE:";       // FUND:UNAPPROVE:<symbol>

    // NEW: multi-exchange selection
    private static final String CB_FUND_TOGGLE_EX = "FUND:TOGEX:";           // FUND:TOGEX:<symbol>:<exchange>
    private static final String CB_FUND_EX_NEXT = "FUND:EXNEXT:";            // FUND:EXNEXT:<symbol>

    private static final String CB_FUND_USDT = "FUND:USDT:";                 // FUND:USDT:<symbol>:<exchange>:<usdt>

    // Arbitrage flow
    private static final String CB_ARB_PICK = "ARB:PICK:";                   // ARB:PICK:<symbol>
    private static final String CB_ARB_LONG = "ARB:LONG:";                   // ARB:LONG:<exchange>
    private static final String CB_ARB_SHORT = "ARB:SHORT:";                 // ARB:SHORT:<exchange>
    private static final String CB_ARB_LEV = "ARB:LEV:";                     // ARB:LEV:<n>
    private static final String CB_ARB_QTY = "ARB:QTY:";                     // ARB:QTY:<qty>
    private static final String CB_ARB_CONFIRM = "ARB:CONFIRM";

    // ---- menu button texts (если у тебя ReplyKeyboard или если Telegram шлёт текст кнопок) ----
    private static final String TXT_BTN_FUNDING = "📌 Funding";
    private static final String TXT_BTN_FUNDING_APPROVED = "✅ Funding approved";
    private static final String TXT_BTN_ARB = "⚡ Arbitrage";
    private static final String TXT_BTN_CANCEL = "❌ Cancel";

    private final String username;
    private final FundingWatchlistService fundingWatchlist;
    private final ArbitrageWatchlistService arbitrageWatchlist;
    private final TelegramSessionStore sessionStore;
    private final TestOrderEngine testOrderEngine;
    private final FundingRefresherService fundingRefresherService;

    // approved funding storage (in-memory)
    private final ApprovedFundingStore fundingStore;
    private final FundingApprovalService fundingApprovalService;

    // pending funding selection per chat (when user is choosing exchanges)
    private final Map<Long, PendingFundingSelection> pendingFundingSelection = new ConcurrentHashMap<>();

    private record PendingFundingSelection(String symbol, Set<String> exchanges)
    {
    }

    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" )
                                                           .withZone( ZoneId.of( "UTC" ) );

    public TelegramBot(
        @Value( "${telegram.bot.username}" ) String username,
        @Value( "${telegram.bot.token}" ) String token,
        FundingWatchlistService fundingWatchlist,
        ArbitrageWatchlistService arbitrageWatchlist,
        TelegramSessionStore sessionStore,
        TestOrderEngine testOrderEngine, FundingRefresherService fundingRefresherService, ApprovedFundingStore fundingStore, FundingApprovalService fundingApprovalService
    )
    {
        super( token );
        this.username = username;
        this.fundingWatchlist = fundingWatchlist;
        this.arbitrageWatchlist = arbitrageWatchlist;
        this.sessionStore = sessionStore;
        this.testOrderEngine = testOrderEngine;
        this.fundingRefresherService = fundingRefresherService;
        this.fundingStore = fundingStore;
        this.fundingApprovalService = fundingApprovalService;
    }

    @Override
    public String getBotUsername()
    {
        return username;
    }

    @Override
    public void onUpdateReceived( Update update )
    {
        try
        {
            if( update.hasCallbackQuery() )
            {
                handleCallback( update );
                return;
            }
            if( update.hasMessage() && update.getMessage().hasText() )
            {
                handleText( update );
            }
        }
        catch( Exception e )
        {
            log.error( "Update failed", e );
            long chatId = update.hasMessage()
                          ? update.getMessage().getChatId()
                          : update.hasCallbackQuery() ? update.getCallbackQuery().getMessage().getChatId() : -1;
            if( chatId != -1 )
            {
                ui( chatId, true, "Ошибка: " + e.getMessage(), menuKb() );
            }
        }
    }

    // ---------------- TEXT ----------------

    private void handleText( Update update )
    {
        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        // 1) кнопки которые приходят текстом — НЕ считаем ручным вводом
        String asCommand = mapButtonTextToCommand( text );
        if( asCommand != null )
        {
            sessionStore.setLastInputWasManual( chatId, false );
            routeCommand( chatId, asCommand, false );
            return;
        }

        // 2) команды (/funding и т.п.) — это уже “ручной ввод”
        if( text.startsWith( "/" ) )
        {
            sessionStore.setLastInputWasManual( chatId, true );
            routeCommand( chatId, text, true );
            return;
        }

        // 3) числовой ввод (USDT для funding approve / QTY для arb) — ручной ввод
        var s = sessionStore.get( chatId );
        sessionStore.setLastInputWasManual( chatId, true );

        if( looksLikeNumber( text ) )
        {
            if( s.state() == TelegramSessionStore.State.FUNDING_APPROVE_SET_USDT
                && s.fundingSymbol() != null
            )
            {
                BigDecimal usdt = new BigDecimal( text );

                PendingFundingSelection p = pendingFundingSelection.get( chatId );
                if( p == null || p.exchanges().isEmpty() || !Objects.equals( p.symbol(), s.fundingSymbol() ) )
                {
                    ui( chatId, true, "Сначала выбери биржи для " + s.fundingSymbol(), fundingExchangeMultiKb( chatId, s.fundingSymbol() ) );
                    return;
                }

                fundingApprovalService.approve( s.fundingSymbol(), Set.copyOf( p.exchanges() ), usdt );

                pendingFundingSelection.remove( chatId );

                sessionStore.set( new TelegramSessionStore.Session(
                    chatId,
                    s.uiMessageId(),
                    true,
                    TelegramSessionStore.State.FUNDING_LIST,
                    null, null,
                    s.arbSymbol(), s.longExchange(), s.shortExchange(), s.leverage(), s.quantity(),
                    null
                ) );

                ui( chatId, true,
                    "✅ Funding approved: " + s.fundingSymbol() +
                    "\nExchanges: " + p.exchanges().stream().sorted().collect( Collectors.joining( ", " ) ) +
                    "\nUSDT: " + usdt,
                    menuKb()
                );
                return;
            }

            if( s.state() == TelegramSessionStore.State.ARB_SET_QTY && s.arbSymbol() != null )
            {
                BigDecimal qty = new BigDecimal( text );
                sessionStore.set( new TelegramSessionStore.Session(
                    chatId,
                    s.uiMessageId(),
                    true,
                    TelegramSessionStore.State.ARB_CONFIRM,
                    null, null,
                    s.arbSymbol(), s.longExchange(), s.shortExchange(), s.leverage(), qty,
                    null
                ) );
                showArbConfirm( chatId, true );
                return;
            }
        }

        ui( chatId, true, "Неизвестная команда.\n\n" + helpText(), menuKb() );
    }

    private String mapButtonTextToCommand( String text )
    {
        return switch( text )
        {
            case TXT_BTN_FUNDING -> "/funding";
            case TXT_BTN_FUNDING_APPROVED -> "/funding_approved";
            case TXT_BTN_ARB -> "/arb";
            case TXT_BTN_CANCEL -> "/cancel";
            default -> null;
        };
    }

    private void routeCommand( long chatId, String cmd, boolean manual )
    {
        switch( cmd )
        {
            case "/start", "/menu" ->
            {
                sessionStore.clear( chatId );
                ui( chatId, manual, helpText(), menuKb() );
            }
            case "/cancel" ->
            {
                sessionStore.clear( chatId );
                ui( chatId, manual, "Ок, отменил.\n\n" + helpText(), menuKb() );
            }
            case "/funding" -> showFunding( chatId, manual );
            case "/funding_approved" -> showFundingApproved( chatId, manual );
            case "/arb" -> showArbSymbols( chatId, manual );
            default -> ui( chatId, manual, "Неизвестная команда: " + cmd + "\n\n" + helpText(), menuKb() );
        }
    }

    // ---------------- CALLBACKS ----------------

    private void handleCallback( Update update )
    {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        String data = update.getCallbackQuery().getData();

        // кнопки — это НЕ ручной ввод → можно обновлять UI (удалить старое и нарисовать новое)
        sessionStore.setLastInputWasManual( chatId, false );

        // поддержка callback data вида "/funding"
        if( data != null && data.startsWith( "/" ) )
        {
            routeCommand( chatId, data, false );
            return;
        }

        switch( data )
        {
            case CB_NAV_MENU ->
            {
                sessionStore.clear( chatId );
                ui( chatId, false, helpText(), menuKb() );
                return;
            }
            case CB_NAV_FUNDING ->
            {
                showFunding( chatId, false );
                return;
            }
            case CB_NAV_FUNDING_APPROVED ->
            {
                showFundingApproved( chatId, false );
                return;
            }
            case CB_NAV_ARB ->
            {
                showArbSymbols( chatId, false );
                return;
            }
            case CB_CANCEL ->
            {
                sessionStore.clear( chatId );
                ui( chatId, false, "Отменено.\n\n" + helpText(), menuKb() );
                return;
            }
        }

        // ---- Funding approve flow ----
        if( data.startsWith( CB_FUND_UNAPPROVE ) )
        {
            String sym = data.substring( CB_FUND_UNAPPROVE.length() );

            fundingApprovalService.unapprove( fundingStore.findBySymbol( sym ).get().getSymbol() );
            ui( chatId, false, "❌ Unapproved: " + sym, menuKb() );
            return;
        }

        if( data.startsWith( CB_FUND_APPROVE ) )
        {
            String sym = data.substring( CB_FUND_APPROVE.length() );

            // init pending selection for this chat
            pendingFundingSelection.put( chatId, new PendingFundingSelection( sym, ConcurrentHashMap.newKeySet() ) );

            var s = sessionStore.get( chatId );
            sessionStore.set( new TelegramSessionStore.Session(
                chatId,
                s.uiMessageId(),
                false,
                TelegramSessionStore.State.FUNDING_APPROVE_SELECT_EXCHANGE,
                sym, null, // fundingExchange больше не используем
                s.arbSymbol(), s.longExchange(), s.shortExchange(), s.leverage(), s.quantity(),
                null
            ) );

            ui( chatId, false, "Выбери биржи для funding (можно несколько): " + sym, fundingExchangeMultiKb( chatId, sym ) );
            return;
        }

        if( data.startsWith( CB_FUND_TOGGLE_EX ) )
        {
            // FUND:TOGEX:<symbol>:<exchange>
            String payload = data.substring( CB_FUND_TOGGLE_EX.length() );
            String[] parts = payload.split( ":" );
            if( parts.length == 2 )
            {
                String sym = parts[0];
                String ex = parts[1];

                PendingFundingSelection p = pendingFundingSelection.computeIfAbsent(
                    chatId,
                    id -> new PendingFundingSelection( sym, ConcurrentHashMap.newKeySet() )
                );

                // если пользователь вдруг кликнул по другому символу — переинициализируем
                if( !Objects.equals( p.symbol(), sym ) )
                {
                    p = new PendingFundingSelection( sym, ConcurrentHashMap.newKeySet() );
                    pendingFundingSelection.put( chatId, p );
                }

                if( p.exchanges().contains( ex ) )
                {
                    p.exchanges().remove( ex );
                }
                else
                {
                    p.exchanges().add( ex );
                }

                String selected = p.exchanges().isEmpty()
                                  ? "(пока ничего не выбрано)"
                                  : p.exchanges().stream().sorted().collect( Collectors.joining( ", " ) );

                ui( chatId, false,
                    "Выбери биржи для funding (можно несколько): " + sym + "\nВыбрано: " + selected,
                    fundingExchangeMultiKb( chatId, sym )
                );
                return;
            }
        }

        if( data.startsWith( CB_FUND_EX_NEXT ) )
        {
            String sym = data.substring( CB_FUND_EX_NEXT.length() );

            PendingFundingSelection p = pendingFundingSelection.get( chatId );
            if( p == null || p.exchanges().isEmpty() || !Objects.equals( p.symbol(), sym ) )
            {
                ui( chatId, false, "Выбери хотя бы одну биржу для " + sym, fundingExchangeMultiKb( chatId, sym ) );
                return;
            }

            var s = sessionStore.get( chatId );
            sessionStore.set( new TelegramSessionStore.Session(
                chatId,
                s.uiMessageId(),
                false,
                TelegramSessionStore.State.FUNDING_APPROVE_SET_USDT,
                sym, null,
                s.arbSymbol(), s.longExchange(), s.shortExchange(), s.leverage(), s.quantity(),
                null
            ) );

            ui( chatId, false,
                "Введи сумму USDT для funding:\nSymbol: " + sym + "\nExchanges: " +
                p.exchanges().stream().sorted().collect( Collectors.joining( ", " ) ),
                fundingUsdtKb( sym )
            );
            return;
        }

        if( data.startsWith( CB_FUND_USDT ) )
        {
            // FUND:USDT:<symbol>:<usdt>
            String payload = data.substring( CB_FUND_USDT.length() );
            String[] parts = payload.split( ":" );
            if( parts.length == 2 )
            {
                String sym = parts[0];
                BigDecimal usdt = new BigDecimal( parts[1] );

                PendingFundingSelection p = pendingFundingSelection.get( chatId );
                if( p == null || p.exchanges().isEmpty() || !Objects.equals( p.symbol(), sym ) )
                {
                    ui( chatId, false, "Сначала выбери биржи для " + sym, fundingExchangeMultiKb( chatId, sym ) );
                    return;
                }

                Instant nextFundingAt = resolveNextFundingAtOrThrow( sym, p.exchanges() );

                fundingApprovalService.approve( sym, Set.copyOf( p.exchanges() ), usdt );
                pendingFundingSelection.remove( chatId );

                ui( chatId, false,
                    "✅ Funding approved:\n" + sym +
                    "\nExchanges: " + p.exchanges().stream().sorted().collect( Collectors.joining( ", " ) ) +
                    "\nUSDT: " + usdt +
                    "\nNext funding: " + dtf.format( nextFundingAt ) + " UTC",
                    menuKb()
                );
                return;
            }
        }

        // ---- Arbitrage flow ----
        if( data.startsWith( CB_ARB_PICK ) )
        {
            String sym = data.substring( CB_ARB_PICK.length() );
            var s = sessionStore.get( chatId );
            sessionStore.set( new TelegramSessionStore.Session(
                chatId, s.uiMessageId(), false,
                TelegramSessionStore.State.ARB_SELECT_LONG_EX,
                null, null,
                sym, null, null, null, null,
                null
            ) );
            ui( chatId, false, "Выбери биржу для LONG по " + sym, exchangeKb( CB_ARB_LONG ) );
            return;
        }

        if( data.startsWith( CB_ARB_LONG ) )
        {
            String ex = data.substring( CB_ARB_LONG.length() );
            var s = sessionStore.get( chatId );
            sessionStore.set( new TelegramSessionStore.Session(
                chatId, s.uiMessageId(), false,
                TelegramSessionStore.State.ARB_SELECT_SHORT_EX,
                null, null,
                s.arbSymbol(), ex, null, null, null,
                null
            ) );
            ui( chatId, false, "Выбери биржу для SHORT", exchangeKb( CB_ARB_SHORT ) );
            return;
        }

        if( data.startsWith( CB_ARB_SHORT ) )
        {
            String ex = data.substring( CB_ARB_SHORT.length() );
            var s = sessionStore.get( chatId );
            sessionStore.set( new TelegramSessionStore.Session(
                chatId, s.uiMessageId(), false,
                TelegramSessionStore.State.ARB_SET_LEVERAGE,
                null, null,
                s.arbSymbol(), s.longExchange(), ex, null, null,
                null
            ) );
            ui( chatId, false, "Выбери плечо (leverage):", leverageKb() );
            return;
        }

        if( data.startsWith( CB_ARB_LEV ) )
        {
            Integer lev = Integer.parseInt( data.substring( CB_ARB_LEV.length() ) );
            var s = sessionStore.get( chatId );
            sessionStore.set( new TelegramSessionStore.Session(
                chatId, s.uiMessageId(), false,
                TelegramSessionStore.State.ARB_SET_QTY,
                null, null,
                s.arbSymbol(), s.longExchange(), s.shortExchange(), lev, null,
                null
            ) );
            ui( chatId, false, "Выбери quantity (или введи числом):", qtyKb( s.arbSymbol() ) );
            return;
        }

        if( data.startsWith( CB_ARB_QTY ) )
        {
            BigDecimal qty = new BigDecimal( data.substring( CB_ARB_QTY.length() ) );
            var s = sessionStore.get( chatId );
            sessionStore.set( new TelegramSessionStore.Session(
                chatId, s.uiMessageId(), false,
                TelegramSessionStore.State.ARB_CONFIRM,
                null, null,
                s.arbSymbol(), s.longExchange(), s.shortExchange(), s.leverage(), qty,
                null
            ) );
            showArbConfirm( chatId, false );
            return;
        }

        if( CB_ARB_CONFIRM.equals( data ) )
        {
            var s = sessionStore.get( chatId );
            // ордер — отдельная история, но пусть хотя бы попытка делается
//            TestOrderResult longRes = testOrderEngine.placeTestOrder(
//                new PlaceTestOrderCommand(
//                    s.longExchange(),
//                    s.arbSymbol(),
//                    OrderSide.BUY,
//                    OrderType.MARKET,
//                    s.quantity(),
//                    null
//                )
//            );
//            TestOrderResult shortRes = testOrderEngine.placeTestOrder(
//                new PlaceTestOrderCommand(
//                    s.shortExchange(),
//                    s.arbSymbol(),
//                    OrderSide.SELL,
//                    OrderType.MARKET,
//                    s.quantity(),
//                    null
//                )
//            );
//
//            sessionStore.clear( chatId );
//
//            ui( chatId, false,
//                "✅ TEST orders sent\n" +
//                "Symbol: " + s.arbSymbol() + "\n" +
//                "LONG: " + s.longExchange() + " (BUY)\n" +
//                "SHORT: " + s.shortExchange() + " (SELL)\n" +
//                "Leverage: " + s.leverage() + "x\n" +
//                "Qty: " + s.quantity() + "\n\n" +
//                "LONG: " + longRes.status() + " id=" + longRes.exchangeOrderId() + "\n" +
//                "SHORT: " + shortRes.status() + " id=" + shortRes.exchangeOrderId(),
//                menuKb()
//            );
            return;
        }

        ui( chatId, false, "Неизвестная кнопка.\n\n" + helpText(), menuKb() );
    }

    // ---------------- SCREENS ----------------

    private void showFunding( long chatId, boolean manual )
    {
        fundingRefresherService.refreshFunding();
        Collection<Item> items = fundingWatchlist.all();
        if( items.isEmpty() )
        {
            ui( chatId, manual, "Funding watchlist пуст.", menuKb() );
            return;
        }

        String text = items.stream()
                           .sorted( Comparator.comparing( Item::symbol ) )
                           .limit( 30 )
                           .map( it -> formatFunding( it.funding() ) )
                           .collect( Collectors.joining( "\n\n" ) );

        ui( chatId, manual, text, fundingListKb( items.stream().map( Item::symbol ).toList(), false ) );
    }

    private void showFundingApproved( long chatId, boolean manual )
    {
        List<ApprovedFundingEntity> items = fundingStore.listPending();
        if( items.isEmpty() )
        {
            ui( chatId, manual, "Утвержденных funding-пар нет.", menuKb() );
            return;
        }

        String text = items.stream()
                           .sorted( Comparator.comparing( ApprovedFundingEntity::getSymbol ) )
                           .map( a -> {
                               String next = a.getNextFundingAt() != null ? dtf.format( a.getNextFundingAt() ) + " UTC" : "n/a";
                               String executed = a.isExecuted() && a.getExecutedAt() != null
                                                 ? dtf.format( a.getExecutedAt() ) + " UTC"
                                                 : "-";
                               return "✅ " + a.getSymbol() + " | " +
                                      a.getExchanges().stream().sorted().collect( Collectors.joining( "," ) ) +
                                      " | " + a.getUsdtAmount() + " USDT" +
                                      " | next=" + next +
                                      " | executed=" + executed;
                           } )
                           .collect( Collectors.joining( "\n" ) );

        ui( chatId, manual, text, fundingListKb( items.stream().map( ApprovedFundingEntity::getSymbol ).toList(), true ) );
    }

    private void showArbSymbols( long chatId, boolean manual )
    {
        var items = arbitrageWatchlist.all();
        if( items.isEmpty() )
        {
            ui( chatId, manual, "Арбитражный watchlist пуст.", menuKb() );
            return;
        }
        ui( chatId, manual, "Выбери пару для арбитража:", arbSymbolsKb( items ) );
    }

    private void showArbConfirm( long chatId, boolean manual )
    {
        var s = sessionStore.get( chatId );
        ui( chatId, manual,
            "Проверь параметры:\n" +
            "Symbol: " + s.arbSymbol() + "\n" +
            "LONG: " + s.longExchange() + "\n" +
            "SHORT: " + s.shortExchange() + "\n" +
            "Leverage: " + s.leverage() + "x\n" +
            "Qty: " + s.quantity() + "\n\n" +
            "Подтвердить?",
            kb( List.of(
                List.of( btn( "✅ Confirm", CB_ARB_CONFIRM ), btn( "❌ Cancel", CB_CANCEL ) ),
                List.of( btn( "⬅️ Menu", CB_NAV_MENU ) )
            ) )
        );
    }

    // ---------------- UI RULES ----------------

    /**
     * Правило:
     * - Если manual==false (нажатие кнопок) — удаляем предыдущее UI-сообщение и шлём новое.
     * - Если manual==true (юзер печатал руками) — НЕ трогаем прошлое, печатаем новое (не редактируем/не удаляем).
     */
    private void ui( long chatId, boolean manual, String text, InlineKeyboardMarkup kb )
    {
        TelegramSessionStore.Session s = sessionStore.get( chatId );
        Integer prevUiId = s.uiMessageId();

        if( !manual && prevUiId != null )
        {
            safeDelete( chatId, prevUiId );
        }

        Integer newId = safeSend( chatId, text, kb );
        if( newId != null )
        {
            sessionStore.setUiMessageId( chatId, newId );
        }
    }

    private void safeDelete( long chatId, int messageId )
    {
        try
        {
            execute( new DeleteMessage( String.valueOf( chatId ), messageId ) );
        }
        catch( Exception e )
        {
            log.debug( "Delete failed chatId={} messageId={}", chatId, messageId, e );
        }
    }

    private Integer safeSend( long chatId, String text, InlineKeyboardMarkup kb )
    {
        try
        {
            SendMessage m = new SendMessage();
            m.setChatId( String.valueOf( chatId ) );
            m.setText( text );
            if( kb != null )
            {
                m.setReplyMarkup( kb );
            }
            var res = execute( m );
            return res.getMessageId();
        }
        catch( Exception e )
        {
            log.error( "Send failed chatId={}", chatId, e );
            return null;
        }
    }

    // ---------------- KEYBOARDS ----------------

    private InlineKeyboardMarkup menuKb()
    {
        return kb( List.of(
            List.of( btn( TXT_BTN_FUNDING, "/funding" ) ),
            List.of( btn( TXT_BTN_FUNDING_APPROVED, "/funding_approved" ) ),
            List.of( btn( TXT_BTN_ARB, "/arb" ) ),
            List.of( btn( TXT_BTN_CANCEL, "/cancel" ) )
        ) );
    }

    private InlineKeyboardMarkup fundingListKb( Collection<String> items, boolean isApproved )
    {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for( String sym : items.stream().sorted().limit( 10 ).toList() )
        {
            boolean isExist = fundingStore.findBySymbolAndActive( sym ) != null;
            if( isApproved )
            {
                rows.add( List.of(
                    btn( "❌ Remove " + sym, CB_FUND_UNAPPROVE + sym )
                ) );
            }
            else
            {
                rows.add( List.of(
                    isExist
                    ? btn( "Update " + sym, CB_FUND_APPROVE + sym )
                    : btn( "✅ Approve " + sym, CB_FUND_APPROVE + sym )
                ) );
            }
        }
        rows.add( List.of( btn( "⬅️ Menu", CB_NAV_MENU ) ) );
        return kb( rows );
    }

    private InlineKeyboardMarkup fundingExchangeMultiKb( long chatId, String symbol )
    {
        PendingFundingSelection p = pendingFundingSelection.get( chatId );
        Set<String> selected = ( p != null && Objects.equals( p.symbol(), symbol ) ) ? p.exchanges() : Set.of();

        return kb( List.of(
            List.of(
                btn( selected.contains( "binance" ) ? "✅ binance" : "☐ binance", CB_FUND_TOGGLE_EX + symbol + ":binance" ),
                btn( selected.contains( "bybit" ) ? "✅ bybit" : "☐ bybit", CB_FUND_TOGGLE_EX + symbol + ":bybit" )
            ),
            List.of(
                btn( selected.contains( "gate" ) ? "✅ gate" : "☐ gate", CB_FUND_TOGGLE_EX + symbol + ":gate" )
            ),
            List.of(
                btn( "➡️ Next", CB_FUND_EX_NEXT + symbol )
            ),
            List.of( btn( "❌ Cancel", CB_CANCEL ), btn( "⬅️ Menu", CB_NAV_MENU ) )
        ) );
    }

    private InlineKeyboardMarkup fundingUsdtKb( String symbol )
    {
        return kb( List.of(
            List.of( btn( "25", CB_FUND_USDT + symbol + ":25" ), btn( "50", CB_FUND_USDT + symbol + ":50" ) ),
            List.of( btn( "100", CB_FUND_USDT + symbol + ":100" ), btn( "250", CB_FUND_USDT + symbol + ":250" ) ),
            List.of( btn( "❌ Cancel", CB_CANCEL ), btn( "⬅️ Menu", CB_NAV_MENU ) )
        ) );
    }

    private InlineKeyboardMarkup arbSymbolsKb( Collection<ArbitrageWatchlistService.Item> items )
    {
        List<List<InlineKeyboardButton>> rows = items.stream()
                                                     .sorted( Comparator.comparing( ArbitrageWatchlistService.Item::symbol ) )
                                                     .limit( 20 )
                                                     .map( i -> List.of( btn( i.symbol(), CB_ARB_PICK + i.symbol() ) ) )
                                                     .collect( Collectors.toCollection( ArrayList::new ) );
        rows.add( List.of( btn( "⬅️ Menu", CB_NAV_MENU ) ) );
        return kb( rows );
    }

    private InlineKeyboardMarkup exchangeKb( String prefix )
    {
        return kb( List.of(
            List.of( btn( "binance", prefix + "binance" ), btn( "bybit", prefix + "bybit" ) ),
            List.of( btn( "gate", prefix + "gate" ) ),
            List.of( btn( "❌ Cancel", CB_CANCEL ), btn( "⬅️ Menu", CB_NAV_MENU ) )
        ) );
    }

    private InlineKeyboardMarkup leverageKb()
    {
        return kb( List.of(
            List.of( btn( "1x", CB_ARB_LEV + "1" ), btn( "2x", CB_ARB_LEV + "2" ), btn( "3x", CB_ARB_LEV + "3" ) ),
            List.of( btn( "5x", CB_ARB_LEV + "5" ), btn( "10x", CB_ARB_LEV + "10" ) ),
            List.of( btn( "❌ Cancel", CB_CANCEL ), btn( "⬅️ Menu", CB_NAV_MENU ) )
        ) );
    }

    private InlineKeyboardMarkup qtyKb( String symbol )
    {
        return kb( List.of(
            List.of( btn( "0.001", CB_ARB_QTY + "0.001" ), btn( "0.01", CB_ARB_QTY + "0.01" ) ),
            List.of( btn( "0.1", CB_ARB_QTY + "0.1" ), btn( "1", CB_ARB_QTY + "1" ) ),
            List.of( btn( "❌ Cancel", CB_CANCEL ), btn( "⬅️ Menu", CB_NAV_MENU ) )
        ) );
    }

    private InlineKeyboardMarkup kb( List<List<InlineKeyboardButton>> rows )
    {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard( rows );
        return m;
    }

    private InlineKeyboardButton btn( String text, String data )
    {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText( text );
        b.setCallbackData( data );
        return b;
    }

    // ---------------- HELPERS ----------------

    private String helpText()
    {
        return "Команды:\n" +
               "/funding — показать watchlist фандинга\n" +
               "/funding_approved — утвержденные пары (биржа + USDT)\n" +
               "/arb — арбитраж: symbol → LONG/SHORT биржи → leverage → qty → confirm\n" +
               "/cancel — сброс";
    }

    private String formatFunding( Map<String, WatchFunding> map )
    {
        if( map == null || map.isEmpty() )
        {
            return "Нет данных по биржам.";
        }
        return map.values().stream()
                  .sorted( Comparator.comparing( WatchFunding::exchange ) )
                  .map( wf -> {
                      String next = ( wf.nextFundingAt() != null && wf.nextFundingAt().toEpochMilli() > 0 )
                                    ? dtf.format( wf.nextFundingAt() ) + " UTC"
                                    : "n/a";
                      return "- " + wf.exchange()
                             + ": " + String.format( Locale.US, "%.4f", wf.fundingRatePct() ) + "%, next=" + next
                             + ", in=" + wf.secondsToFunding() + "s";
                  } )
                  .collect( Collectors.joining( "\n" ) );
    }

    private boolean looksLikeNumber( String s )
    {
        return s.matches( "^[0-9]+(\\.[0-9]+)?$" );
    }

    private Instant resolveNextFundingAtOrThrow( String symbol, Set<String> selectedExchanges )
    {
        FundingWatchlistService.Item item = fundingWatchlist.all().stream()
                                                            .filter( i -> Objects.equals( i.symbol(), symbol ) )
                                                            .findFirst()
                                                            .orElseThrow( () -> new IllegalStateException( "Funding data not found for symbol: " + symbol ) );

        Map<String, WatchFunding> map = item.funding();
        if( map == null || map.isEmpty() )
        {
            throw new IllegalStateException( "Funding data is empty for symbol: " + symbol );
        }

        // 1) пробуем взять nextFundingAt из выбранных бирж
        for( String ex : selectedExchanges )
        {
            WatchFunding wf = map.get( ex );
            if( wf != null && wf.nextFundingAt() != null && wf.nextFundingAt().toEpochMilli() > 0 )
            {
                return wf.nextFundingAt();
            }
        }

        // 2) fallback: любая биржа, где nextFundingAt валиден
        return map.values().stream()
                  .map( WatchFunding::nextFundingAt )
                  .filter( Objects::nonNull )
                  .filter( t -> t.toEpochMilli() > 0 )
                  .findFirst()
                  .orElseThrow( () -> new IllegalStateException( "nextFundingAt is not available yet for symbol: " + symbol ) );
    }
}
