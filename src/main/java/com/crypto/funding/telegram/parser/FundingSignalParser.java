package com.crypto.funding.telegram.parser;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FundingSignalParser {

    // пример строки:
    // "coin: KERNEL/USDT:USDT"
    // "coin: ME/USDT:USDT"
    private static final Pattern COIN_LINE =
        Pattern.compile("coin:\\s*([^\\r\\n]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Возвращает список тикеров в формате "BASE/USDT".
     * Если нет валидных тикеров - вернет пустой список.
     */
    public List<String> extractSymbols(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            return List.of();
        }

        String text = messageText.trim();

        Matcher m = COIN_LINE.matcher(text);
        if (!m.find()) {
            // нет coin: -> значит это не funding сообщение
            return List.of();
        }

        // Пример: "KERNEL/USDT:USDT"
        // или "ME/USDT:USDT"
        String unifiedGuess = getSymbol( m );

        // sanity check: должен быть вид "SOMETHING/USDT"
        if (!unifiedGuess.contains("/")) {
            // если канал вдруг даст "BTCUSDT:USDT",
            // можно попытаться вставить "/", но пока не будем гадать:
            return List.of();
        }

        return List.of(unifiedGuess);
    }

    private static String getSymbol( Matcher m )
    {
        String rawCoinField = m.group(1).trim().toUpperCase(Locale.ROOT);

        // Берём всё до первого ":"  => "KERNEL/USDT"

        // Нормализуем разделитель "/" между базой и котировкой
        // Нам важно получить ровно "BASE/USDT".
        // Если внезапно там будет "XXX/USDC" – мы это тоже сохраним как есть.
        // Потом SymbolMapper.toUnified() всё равно приведёт к нужному формату.
        // Но сейчас логично оставить именно то, что до ":".
        String unifiedGuess = rawCoinField.split(":", 2)[0].trim();
        return unifiedGuess;
    }
}
