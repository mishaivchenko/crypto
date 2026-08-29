package com.crypto.funding.telegram.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram.bot")
public record TelegramBotProperties(
        String token, String notificationChatId, String allowedUserIds, long signalPollIntervalMs) {
    public List<Long> allowedUserIdList() {
        if (allowedUserIds == null || allowedUserIds.isBlank()) {
            return List.of();
        }
        return List.of(allowedUserIds.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .toList();
    }

    public boolean hasNotificationTarget() {
        return notificationChatId != null && !notificationChatId.isBlank();
    }

    public long notificationChatIdLong() {
        return Long.parseLong(notificationChatId);
    }
}
