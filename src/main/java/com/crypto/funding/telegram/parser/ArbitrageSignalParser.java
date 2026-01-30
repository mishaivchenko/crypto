package com.crypto.funding.telegram.parser;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ArbitrageSignalParser {

    // Ищем строку "Монеты: <что-то до перевода строки>"
    // Пример совпадения: "Монеты: SD, ARKM, ETH"
    private static final Pattern COINS_LINE =
        Pattern.compile("Монеты:\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Токен монеты: буквы+цифры, иногда есть точки или дефисы у шиткоинов,
    // но давай пока разрешим [A-Z0-9]+ чтобы не тянуть мусор типа "Монеты".
    private static final Pattern COIN_SYMBOL =
        Pattern.compile("([A-Z0-9]+)");

    /**
     * Возвращает список тикеров в нормализованном виде "SYMBOL/USDT".
     * Если в сообщении монет нет - вернет пустой список.
     */
    public List<String> extractSymbols(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            return List.of();
        }

        String textUpper = messageText.toUpperCase(Locale.ROOT);

        Matcher m = COINS_LINE.matcher(textUpper);

        if (!m.find()) {
            // нет строки "Монеты: ..." -> значит это не арбитражное сообщение
            return List.of();
        }

        String coinsPart = m.group(1); // "SD, ARKM, ETH"
        if (coinsPart == null) {
            return List.of();
        }

        Set<String> result = getSymbols( coinsPart );

        return new ArrayList<>(result);
    }

    private static Set<String> getSymbols( String coinsPart )
    {
        Set<String> result = new LinkedHashSet<>();
        Matcher c = COIN_SYMBOL.matcher( coinsPart );

        while (c.find()) {
            String coin = c.group(1);
            // Отсекаем заведомо левые слова
            if (coin.equals("MONETЫ") || coin.equals("MONETY") || coin.equals("COINS")) {
                continue;
            }
            // Простейшая защита от очень коротких или очень длинных "слов"
            if (coin.length() < 2 || coin.length() > 15) {
                continue;
            }

            // нормализуем в формат BASE/USDT
            String unified = coin + "/USDT";
            result.add(unified);
        }
        return result;
    }
}
