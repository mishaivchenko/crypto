package com.crypto.funding.telegram;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TelegramSessionStore {

    public enum State {
        IDLE,

        FUNDING_LIST,
        FUNDING_APPROVE_SELECT_EXCHANGE,
        FUNDING_APPROVE_SET_USDT,

        ARB_SELECT_SYMBOL,
        ARB_SELECT_LONG_EX,
        ARB_SELECT_SHORT_EX,
        ARB_SET_LEVERAGE,
        ARB_SET_QTY,
        ARB_CONFIRM
    }

    public record Session(
        long chatId,
        Integer uiMessageId,

        // если true — юзер вводил руками; следующее сообщение НЕ апдейтим, а печатаем новое
        boolean lastInputWasManual,

        State state,

        // Funding approve flow
        String fundingSymbol,
        String fundingExchange,

        // Arbitrage flow
        String arbSymbol,
        String longExchange,
        String shortExchange,
        Integer leverage,
        BigDecimal quantity,

        Instant updatedAt
    ) {}

    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    public Session get(long chatId) {
        return sessions.getOrDefault(
            chatId,
            new Session(
                chatId,
                null,
                false,
                State.IDLE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
            )
        );
    }

    public void set(Session s) {
        sessions.put(
            s.chatId(),
            new Session(
                s.chatId(),
                s.uiMessageId(),
                s.lastInputWasManual(),
                s.state(),
                s.fundingSymbol(),
                s.fundingExchange(),
                s.arbSymbol(),
                s.longExchange(),
                s.shortExchange(),
                s.leverage(),
                s.quantity(),
                Instant.now()
            )
        );
    }

    public void clear(long chatId) {
        sessions.remove(chatId);
    }

    public void setUiMessageId(long chatId, Integer uiMessageId) {
        Session s = get(chatId);
        set(new Session(
            chatId,
            uiMessageId,
            s.lastInputWasManual(),
            s.state(),
            s.fundingSymbol(),
            s.fundingExchange(),
            s.arbSymbol(),
            s.longExchange(),
            s.shortExchange(),
            s.leverage(),
            s.quantity(),
            Instant.now()
        ));
    }

    public void setLastInputWasManual(long chatId, boolean manual) {
        Session s = get(chatId);
        set(new Session(
            chatId,
            s.uiMessageId(),
            manual,
            s.state(),
            s.fundingSymbol(),
            s.fundingExchange(),
            s.arbSymbol(),
            s.longExchange(),
            s.shortExchange(),
            s.leverage(),
            s.quantity(),
            Instant.now()
        ));
    }
}
