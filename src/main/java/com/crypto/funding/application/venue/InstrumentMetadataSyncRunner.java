package com.crypto.funding.application.venue;

import com.crypto.funding.config.MetadataSyncProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InstrumentMetadataSyncRunner implements ApplicationRunner
{
    private final MetadataSyncProperties metadataSyncProperties;
    private final InstrumentRegistryService instrumentRegistryService;

    public InstrumentMetadataSyncRunner(
        MetadataSyncProperties metadataSyncProperties,
        InstrumentRegistryService instrumentRegistryService
    )
    {
        this.metadataSyncProperties = metadataSyncProperties;
        this.instrumentRegistryService = instrumentRegistryService;
    }

    @Override
    public void run( ApplicationArguments args )
    {
        if( metadataSyncProperties.isSyncOnStartup() )
        {
            instrumentRegistryService.syncVenues( metadataSyncProperties.getEnabledVenues() );
        }
    }

    @Scheduled(fixedDelayString = "${trading.metadata.sync-interval-minutes:240}m")
    public void scheduledSync()
    {
        if( metadataSyncProperties.isScheduleEnabled() )
        {
            instrumentRegistryService.syncVenues( metadataSyncProperties.getEnabledVenues() );
        }
    }
}
