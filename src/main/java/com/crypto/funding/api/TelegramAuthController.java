package com.crypto.funding.api;

import com.crypto.funding.telegram.TelegramLoginService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping( "/api/telegram" )
public class TelegramAuthController
{
    private final TelegramLoginService login;

    public TelegramAuthController( TelegramLoginService login )
    {
        this.login = login;
    }

    @PostMapping( "/code" )
    public ResponseEntity<?> code( @RequestBody Map<String, String> body )
    {
        login.submitCode( body.getOrDefault( "code", "" ) );
        return ResponseEntity.ok( Map.of( "ok", true ) );
    }

    @PostMapping( "/password" )
    public ResponseEntity<?> password( @RequestBody Map<String, String> body )
    {
        login.submitPassword( body.getOrDefault( "password", "" ) );
        return ResponseEntity.ok( Map.of( "ok", true ) );
    }

    @GetMapping("/api/telegram/status")
    public Map<String, Object> status() {
        return Map.of("loggedIn", login.isLoggedIn());
    }
}
