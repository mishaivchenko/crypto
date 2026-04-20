package com.crypto.funding.infrastructure.source;

import com.crypto.funding.application.candidate.IngestSignalCandidateCommand;
import com.crypto.funding.application.candidate.SignalCandidateIngestService;
import com.crypto.funding.application.port.SymbolMetadataPort;
import com.crypto.funding.config.FundingCandidateSourceProperties;
import com.crypto.funding.config.MetadataSyncProperties;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.infrastructure.persistence.model.SignalCandidateEntity;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import com.crypto.funding.symbol.SymbolMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
    private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;
    private final FundingCandidateSourceProperties sourceProperties;
    private final MetadataSyncProperties metadataSyncProperties;
    private final SignalCandidateIngestService signalCandidateIngestService;
    private final SymbolMetadataPort symbolMetadataPort;
    private final VenueRequestTimingService venueRequestTimingService;
    private final SignalCandidateJpaRepository signalCandidateRepository;
    private final Clock clock;

    @Autowired
    public FundingApiCandidateSourceService(
        HttpClient httpClient,
        VenueHttpProperties venueHttpProperties,
        FundingCandidateSourceProperties sourceProperties,
        MetadataSyncProperties metadataSyncProperties,
        SignalCandidateIngestService signalCandidateIngestService,
        SymbolMetadataPort symbolMetadataPort,
        VenueRequestTimingService venueRequestTimingService,
        SignalCandidateJpaRepository signalCandidateRepository
    )
    {
        this(
            httpClient,
            venueHttpProperties,
            sourceProperties,
            metadataSyncProperties,
            signalCandidateIngestService,
            symbolMetadataPort,
            venueRequestTimingService,
            signalCandidateRepository,
            Clock.systemUTC()
        );
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
        this.httpClient = httpClient;
        this.venueHttpProperties = venueHttpProperties;
        this.sourceProperties = sourceProperties;
        this.metadataSyncProperties = metadataSyncProperties;
        this.signalCandidateIngestService = signalCandidateIngestService;
        this.symbolMetadataPort = symbolMetadataPort;
        this.venueRequestTimingService = venueRequestTimingService;
        this.signalCandidateRepository = signalCandidateRepository;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup()
    {
        refreshCandidates();
    }

    @Scheduled(fixedDelayString = "${trading.candidate-source.refresh-interval-seconds:60}000")
    public void scheduledRefresh()
    {
        refreshCandidates();
    }

    public void refreshCandidates()
    {
        long startNanos = System.nanoTime();
        try
        {
            FundingApiResponse response = fetchPayload();
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

                Optional<ResolvedSymbol> resolvedSymbol = resolveSymbol( entry );
                if( resolvedSymbol.isEmpty() )
                {
                    log.debug( "[candidate-source] skip unresolved entry exchange={} symbol={} coin={}",
                        entry.exchange(), entry.symbol(), entry.coin() );
                    continue;
                }

                FundingObservation observation = toFundingObservation( entry, resolvedSymbol.get().canonicalSymbol() );

                long syntheticMessageId = syntheticMessageId( entry, observation.symbol() );
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

            cleanupStalePendingCandidates( activeSourceMessageIds );

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

    private FundingApiResponse fetchPayload() throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( sourceProperties.getUrl() ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .GET()
                                         .build();

        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        if( response.statusCode() >= 300 )
        {
            throw new IOException( "Funding API request failed: " + response.statusCode() + " body=" + response.body() );
        }
        FundingApiResponse payload = objectMapper.readValue( response.body(), FundingApiResponse.class );
        return payload == null ? new FundingApiResponse( List.of() ) : payload;
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

    private Optional<ResolvedSymbol> resolveSymbol( FundingApiEntry entry )
    {
        String venue = entry.exchange().trim().toLowerCase( Locale.ROOT );
        List<String> candidates = new ArrayList<>();
        addCandidate( candidates, entry.symbol() );
        addCandidate( candidates, entry.coin() );

        for( String candidate : candidates )
        {
            Optional<String> canonicalByVenueSymbol = symbolMetadataPort.findByVenueSymbol( venue, candidate ).map( symbol -> symbol.symbol() );
            if( canonicalByVenueSymbol.isPresent() )
            {
                String canonical = canonicalByVenueSymbol.get();
                return Optional.of( new ResolvedSymbol( candidate, canonical ) );
            }
        }

        for( String candidate : candidates )
        {
            String unified = SymbolMapper.toUnified( candidate );
            if( unified != null && symbolMetadataPort.findSymbolMetadata( venue, unified ).isPresent() )
            {
                return Optional.of( new ResolvedSymbol( candidate, unified ) );
            }
        }

        for( String candidate : candidates )
        {
            String fallback = SymbolMapper.toUnified( candidate );
            if( fallback != null && !fallback.isBlank() )
            {
                return Optional.of( new ResolvedSymbol( candidate, fallback ) );
            }
        }

        return Optional.empty();
    }

    private FundingObservation toFundingObservation( FundingApiEntry entry, String canonicalSymbol )
    {
        Instant referenceTime = parseUpdatedAt( entry.updatedAt() );
        Instant nextFundingAt = computeNextFundingAt( referenceTime, entry.fundingInterval() );
        return new FundingObservation(
            referenceTime,
            canonicalSymbol,
            nextFundingAt,
            decimalOrNull( entry.funding() )
        );
    }

    private void cleanupStalePendingCandidates( Set<Long> activeSourceMessageIds )
    {
        String sourceType = sourceProperties.getSourceType().trim().toUpperCase( Locale.ROOT );
        List<SignalCandidateEntity> staleCandidates = signalCandidateRepository
            .findAllBySourceTypeAndSourceChatIdAndFundingEventIdIsNullOrderByDetectedAtDesc( sourceType, SOURCE_STREAM_ID )
            .stream()
            .filter( candidate -> candidate.getSourceMessageId() != null )
            .filter( candidate -> !activeSourceMessageIds.contains( candidate.getSourceMessageId() ) )
            .filter( candidate -> candidate.getStatus() != SignalCandidateStatus.REJECTED )
            .filter( candidate -> candidate.getStatus() != SignalCandidateStatus.EVENT_CREATED )
            .toList();
        if( !staleCandidates.isEmpty() )
        {
            signalCandidateRepository.deleteAll( staleCandidates );
        }
    }

    private Instant parseUpdatedAt( String rawUpdatedAt )
    {
        if( rawUpdatedAt == null || rawUpdatedAt.isBlank() )
        {
            return Instant.now( clock );
        }
        try
        {
            return LocalDateTime.parse( rawUpdatedAt.trim(), UPDATED_AT_FORMAT ).toInstant( ZoneOffset.UTC );
        }
        catch( DateTimeParseException ex )
        {
            log.debug( "[candidate-source] failed to parse updated_at='{}', fallback to now", rawUpdatedAt, ex );
            return Instant.now( clock );
        }
    }

    private Instant computeNextFundingAt( Instant referenceTime, Integer fundingIntervalHours )
    {
        int intervalHours = fundingIntervalHours == null || fundingIntervalHours <= 0 ? 8 : fundingIntervalHours;
        ZonedDateTime utc = referenceTime.atZone( ZoneOffset.UTC ).truncatedTo( ChronoUnit.HOURS );
        ZonedDateTime midnight = utc.toLocalDate().atStartOfDay( ZoneOffset.UTC );
        int currentHour = utc.getHour();
        int nextBucketHour = ( ( currentHour / intervalHours ) + 1 ) * intervalHours;
        ZonedDateTime nextFunding = midnight.plusHours( nextBucketHour );
        Instant now = Instant.now( clock );
        if( !nextFunding.isAfter( utc ) )
        {
            nextFunding = nextFunding.plusHours( intervalHours );
        }
        while( !nextFunding.toInstant().isAfter( now ) )
        {
            nextFunding = nextFunding.plusHours( intervalHours );
        }
        return nextFunding.toInstant();
    }

    private long syntheticMessageId( FundingApiEntry entry, String symbol )
    {
        if( entry.id() != null )
        {
            return Integer.toUnsignedLong( ( entry.exchange() + ":" + entry.id() ).hashCode() );
        }
        return Integer.toUnsignedLong( ( entry.exchange() + ":" + symbol ).hashCode() );
    }

    private void addCandidate( List<String> candidates, String value )
    {
        if( value == null || value.isBlank() )
        {
            return;
        }
        String normalized = value.trim().toUpperCase( Locale.ROOT );
        if( !candidates.contains( normalized ) )
        {
            candidates.add( normalized );
        }
    }

    private BigDecimal decimalOrNull( String raw )
    {
        if( raw == null || raw.isBlank() )
        {
            return null;
        }
        return new BigDecimal( raw );
    }

    private record ResolvedSymbol(
        String candidateRawSymbol,
        String canonicalSymbol
    )
    {
    }

    private record FundingObservation(
        Instant detectedAt,
        String symbol,
        Instant fundingTime,
        BigDecimal fundingRatePct
    )
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FundingApiResponse( List<FundingApiEntry> data )
    {
        private FundingApiResponse
        {
            data = data == null ? List.of() : List.copyOf( new LinkedHashSet<>( data ) );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FundingApiEntry(
        Long id,
        String symbol,
        String coin,
        String exchange,
        @JsonProperty("exchange_name") String exchangeName,
        String price,
        String funding,
        @JsonProperty("funding_prev") String fundingPrev,
        @JsonProperty("funding_24") String funding24,
        @JsonProperty("funding_3d") String funding3d,
        @JsonProperty("funding_7d") String funding7d,
        @JsonProperty("funding_interval") Integer fundingInterval,
        @JsonProperty("updated_at") String updatedAt
    )
    {
    }
}
