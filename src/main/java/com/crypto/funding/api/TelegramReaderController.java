package com.crypto.funding.api;

import com.crypto.funding.telegram.TelegramReaderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping( "/api/telegram" )
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelegramReaderController
{
    private final TelegramReaderService reader;

    public TelegramReaderController( TelegramReaderService reader )
    {
        this.reader = reader;
    }

    @GetMapping( "/last" )
    public ResponseEntity<?> last(
        @RequestParam String chat,
        @RequestParam( required = false, defaultValue = "20" ) int count,
        @RequestParam( required = false, defaultValue = "funding" ) String type )
    {
        var chatId = reader.resolveChatId( chat ).orElse( null );
        if( chatId == null )
        {
            return ResponseEntity.status( 404 ).body( Map.of(
                "error", "chat_not_found_or_no_access",
                "hint", "Проверь @username/ссылку, либо канал приватный/возрастной. Для invite используйте t.me/+...."
            ) );
        }
        var message = reader.readLastMessage( chatId, count, type );

        return ResponseEntity.ok( Map.of(
            "chatId", chatId,
            "message", message
        ) );
    }

}
