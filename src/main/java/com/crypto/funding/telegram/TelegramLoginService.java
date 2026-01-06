package com.crypto.funding.telegram;

import it.tdlight.client.AuthenticationSupplier;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TelegramLoginService
{
    private final SimpleTelegramClient client;
    private final AtomicBoolean loggedIn = new AtomicBoolean(false);

    public TelegramLoginService(
        SimpleTelegramClientFactory factory,
        TDLibSettings settings,
        @Value("${telegram.phoneNumber}") String phone
    ) {
        // В новой API авторизация задаётся supplier’ом при сборке клиента:
        this.client = factory.builder(settings).build(AuthenticationSupplier.user(phone));

        client.send(new TdApi.SetLogVerbosityLevel(0));
        client.send(new TdApi.SetLogStream(new TdApi.LogStreamEmpty()));

        client.addUpdateHandler(TdApi.UpdateAuthorizationState.class, upd -> {
            if (upd.authorizationState instanceof TdApi.AuthorizationStateReady) {
                loggedIn.set(true);
            }
            // Полезные подсказки по стадиям
            if (upd.authorizationState instanceof TdApi.AuthorizationStateWaitCode) {
                System.out.println("TDLib: ждём код (SMS/Telegram)");
            }
            if (upd.authorizationState instanceof TdApi.AuthorizationStateWaitPassword) {
                System.out.println("TDLib: ждём 2FA пароль");
            }
        });
    }

    public SimpleTelegramClient client() { return client; }
    public boolean isLoggedIn() { return loggedIn.get(); }

    // Эти методы дергаем из REST-контроллера
    public void submitCode(String code) {
        client.send(new TdApi.CheckAuthenticationCode(code));
    }

    public void submitPassword(String password) {
        client.send(new TdApi.CheckAuthenticationPassword(password));
    }
}
