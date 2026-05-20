package com.crypto.funding.infrastructure.source;

import com.crypto.funding.application.candidate.IngestSignalCandidateCommand;
import com.crypto.funding.application.candidate.SignalCandidateIngestService;
import com.crypto.funding.application.port.SymbolMetadataPort;
import com.crypto.funding.config.FundingCandidateSourceProperties;
import com.crypto.funding.config.MetadataSyncProperties;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "trading.candidate-source", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FundingApiCandidateSourceService
{
    private static final Logger log = LoggerFactory.getLogger( FundingApiCandidateSourceService.class );
    private static final String TIMING_VENUE = "candidate-source";
    private static final String TIMING_OPERATION = "uainvest-funding-fetch";
    private static final long SOURCE_STREAM_ID = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FundingCandidateSourceProperties sourceProperties;
    private final MetadataSyncProperties metadataSyncProperties;
    private final SignalCandidateIngestService signalCandidateIngestService;
    private final VenueRequestTimingService venueRequestTimingService;
    private final FundingApiPayloadFetcher payloadFetcher;
    private final FundingApiSymbolResolver symbolResolver;
    private final FundingObservationMapper observationMapper;
    private final StalePendingCandidateCleanupService cleanupService;

    @Autowired
    public FundingApiCandidateSourceService(
        FundingCandidateSourceProperties sourceProperties,
        MetadataSyncProperties metadataSyncProperties,
        SignalCandidateIngestService signalCandidateIngestService,
        VenueRequestTimingService venueRequestTimingService,
        FundingApiPayloadFetcher payloadFetcher,
        FundingApiSymbolResolver symbolResolver,
        FundingObservationMapper observationMapper,
        StalePendingCandidateCleanupService cleanupService
    )
    {
        this.sourceProperties = sourceProperties;
        this.metadataSyncProperties = metadataSyncProperties;
        this.signalCandidateIngestService = signalCandidateIngestService;
        this.venueRequestTimingService = venueRequestTimingService;
        this.payloadFetcher = payloadFetcher;
        this.symbolResolver = symbolResolver;
        this.observationMapper = observationMapper;
        this.cleanupService = cleanupService;
    }

    FundingApiCandidateSourceService(
        HttpClient httpClient,
        VenueHttpProperties venueHttpProperties,
        FundingCandidateSourceProperties sourceProperties,
        MetadataSyncProperties metadataSyncProperties,
        SignalCandidateIngestService signalCandidateIngestService,
        SymbolMetadataPort symbolMetadataPort,
        VenueRequestTimingService venueRequestTimingService,
        SignalCandidateJpaRepository signalCandidateRepository,
        Clock clock
    )
    {
        this.sourceProperties = sourceProperties;
        this.metadataSyncProperties = metadataSyncProperties;
        this.signalCandidateIngestService = signalCandidateIngestService;
        this.venueRequestTimingService = venueRequestTimingService;
        this.payloadFetcher = new FundingApiPayloadFetcher( httpClient, venueHttpProperties, sourceProperties );
        this.symbolResolver = new FundingApiSymbolResolver( symbolMetadataPort );
        this.observationMapper = new FundingObservationMapper( clock );
        this.cleanupService = new StalePendingCandidateCleanupService( signalCandidateRepository, sourceProperties );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup()
    {
        refreshCandidates();
    }

    @Scheduled(fixedDelayString = "${trading.candidate-source.refresh-interval-seconds:60}000")
    public void scheduledRefresh()
    {
        try
        {
            // Prevent thundering herd on startup
            Thread.sleep( 30_000 );
        }
        catch( InterruptedException e )
        {
            Thread.currentThread().interrupt();
        }
        refreshCandidates();
    }

    public void refreshCandidates()
    {
        long startNanos = System.nanoTime();
        try
        {
            FundingApiResponse response = payloadFetcher.fetch();
            int processed = 0;
            Set<Long> activeSourceMessageIds = new LinkedHashSet<>();
            for( FundingApiEntry entry : response.data() )
            {
                if( entry == null )
                {
                    continue;
                }
                if( !isEnabledVenue( entry.exchange() ) )
                {
                    continue;
                }

                Optional<ResolvedFundingSymbol> resolvedSymbol = symbolResolver.resolve( entry );
                if( resolvedSymbol.isEmpty() )
                {
                    log.debug( "[candidate-source] skip unresolved entry exchange={} symbol={} coin={}",
                        entry.exchange(), entry.symbol(), entry.coin() );
                    continue;
                }

                FundingObservation observation = observationMapper.toObservation( entry, resolvedSymbol.get().canonicalSymbol() );
                long syntheticMessageId = observationMapper.syntheticMessageId( entry, observation.symbol() );
                activeSourceMessageIds.add( syntheticMessageId );

                signalCandidateIngestService.ingest( new IngestSignalCandidateCommand(
                    sourceProperties.getSourceType(),
                    SOURCE_STREAM_ID,
                    syntheticMessageId,
                    objectMapper.writeValueAsString( entry ),
                    entry.exchange(),
                    resolvedSymbol.get().candidateRawSymbol(),
                    observation.detectedAt(),
                    observation.fundingTime(),
                    observation.fundingRatePct()
                ) );
                processed++;
            }

            cleanupService.deleteMissingPendingCandidates( activeSourceMessageIds );

            venueRequestTimingService.recordSuccess(
                TIMING_VENUE,
                TIMING_OPERATION,
                System.nanoTime() - startNanos,
                response.data().size(),
                200
            );
            log.info( "[candidate-source] refreshed candidates from funding API entries={} processed={}", response.data().size(), processed );
        }
        catch( Exception ex )
        {
            venueRequestTimingService.recordFailure( TIMING_VENUE, TIMING_OPERATION, System.nanoTime() - startNanos, ex.getMessage() );
            log.warn( "[candidate-source] refresh failed: {}", ex.getMessage(), ex );
        }
    }

    private boolean isEnabledVenue( String rawVenue )
    {
        if( rawVenue == null || rawVenue.isBlank() )
        {
            return false;
        }
        String venue = rawVenue.trim().toLowerCase( Locale.ROOT );
        return metadataSyncProperties.getEnabledVenues().stream()
                                     .map( value -> value.trim().toLowerCase( Locale.ROOT ) )
                                     .anyMatch( venue::equals );
    }
}
