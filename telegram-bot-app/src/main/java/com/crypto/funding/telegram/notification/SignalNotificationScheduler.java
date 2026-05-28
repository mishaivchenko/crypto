package com.crypto.funding.telegram.notification;

import com.crypto.funding.telegram.bot.FundingBot;
import com.crypto.funding.telegram.bot.MessageFormatter;
import com.crypto.funding.telegram.client.MonitorApiClient;
import com.crypto.funding.telegram.client.PageResponse;
import com.crypto.funding.telegram.client.dto.CandidateSummary;
import com.crypto.funding.telegram.config.MonitorProperties;
import com.crypto.funding.telegram.config.TelegramBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "telegram.bot.token", matchIfMissing = false)
public class SignalNotificationScheduler
{
    private static final Logger log = LoggerFactory.getLogger( SignalNotificationScheduler.class );

    private final MonitorApiClient monitorApiClient;
    private final FundingBot fundingBot;
    private final NotificationState state;
    private final TelegramBotProperties botProperties;
    private final MonitorProperties monitorProperties;

    public SignalNotificationScheduler(
        MonitorApiClient monitorApiClient,
        FundingBot fundingBot,
        NotificationState state,
        TelegramBotProperties botProperties,
        MonitorProperties monitorProperties
    )
    {
        this.monitorApiClient = monitorApiClient;
        this.fundingBot = fundingBot;
        this.state = state;
        this.botProperties = botProperties;
        this.monitorProperties = monitorProperties;
    }

    @Scheduled(fixedDelayString = "${telegram.bot.signal-poll-interval-ms:30000}")
    public void pollAndNotify()
    {
        if( !botProperties.hasNotificationTarget() )
        {
            return;
        }

        try
        {
            PageResponse<CandidateSummary> page = monitorApiClient.getCandidates( "NORMALIZED", 20, 0 );
            List<CandidateSummary> candidates = page.content() != null ? page.content() : List.of();

            for( CandidateSummary candidate : candidates )
            {
                String key = "signal:" + candidate.id();
                if( state.isNew( key ) )
                {
                    sendAlert( candidate );
                    state.markSeen( key );
                }
            }
        }
        catch( Exception e )
        {
            log.warn( "Could not poll monitor for new signals: {}", e.getMessage() );
        }
    }

    private void sendAlert( CandidateSummary candidate )
    {
        String uiUrl = monitorProperties.publicUrl();
        String text = MessageFormatter.newSignalAlert( candidate, uiUrl );
        long chatId = botProperties.notificationChatIdLong();
        fundingBot.sendAlert( chatId, text );
        log.info( "Sent signal alert for candidate {} ({})", candidate.id(), candidate.displaySymbol() );
    }
}
