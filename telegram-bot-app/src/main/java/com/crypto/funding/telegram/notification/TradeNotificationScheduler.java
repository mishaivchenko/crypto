package com.crypto.funding.telegram.notification;

import com.crypto.funding.telegram.bot.FundingBot;
import com.crypto.funding.telegram.bot.MessageFormatter;
import com.crypto.funding.telegram.client.MonitorApiClient;
import com.crypto.funding.telegram.client.dto.ArmedTradeSummary;
import com.crypto.funding.telegram.config.TelegramBotProperties;
import com.crypto.funding.telegram.ngrok.NgrokTunnelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "telegram.bot.token", matchIfMissing = false)
public class TradeNotificationScheduler
{
    private static final Logger log = LoggerFactory.getLogger( TradeNotificationScheduler.class );

    private final MonitorApiClient monitorApiClient;
    private final FundingBot fundingBot;
    private final NotificationState state;
    private final TelegramBotProperties botProperties;
    private final NgrokTunnelService ngrokTunnelService;

    public TradeNotificationScheduler(
        MonitorApiClient monitorApiClient,
        FundingBot fundingBot,
        NotificationState state,
        TelegramBotProperties botProperties,
        NgrokTunnelService ngrokTunnelService
    )
    {
        this.monitorApiClient = monitorApiClient;
        this.fundingBot = fundingBot;
        this.state = state;
        this.botProperties = botProperties;
        this.ngrokTunnelService = ngrokTunnelService;
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
            List<ArmedTradeSummary> trades = monitorApiClient.getArmedTrades( false );
            if( trades == null )
            {
                return;
            }
            for( ArmedTradeSummary trade : trades )
            {
                String tradeKey = "trade:" + trade.id();
                if( state.isNew( tradeKey ) )
                {
                    sendAlert( trade );
                    state.markSeen( tradeKey );
                }
            }
        }
        catch( Exception e )
        {
            log.debug( "Could not poll monitor for new trades: {}", e.getMessage() );
        }
    }

    private void sendAlert( ArmedTradeSummary trade )
    {
        String uiUrl = ngrokTunnelService.fetchTunnels()
            .map( NgrokTunnelService.NgrokTunnels::monitorUrl )
            .orElse( null );
        String text = MessageFormatter.newTradeAlert( trade, uiUrl );
        long chatId = botProperties.notificationChatIdLong();
        fundingBot.sendAlert( chatId, text );
        log.info( "Sent trade alert for armed trade {} ({}/{})", trade.id(), trade.venue(), trade.displaySymbol() );
    }
}
