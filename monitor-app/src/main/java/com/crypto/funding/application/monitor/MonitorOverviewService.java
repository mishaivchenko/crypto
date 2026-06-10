package com.crypto.funding.application.monitor;

import com.crypto.funding.api.dto.EnrichmentFreshnessSnapshot;
import com.crypto.funding.application.venue.VenueDiagnosticsService;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.application.event.FundingEventLifecycleService;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.LiquidityAssessmentJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MonitorOverviewService
{
    public record Overview(
        String version,
        String globalAccessMode,
        boolean globalModeOverridden,
        long pendingCandidates,
        long fundingEvents,
        long discoveredEvents,
        long armedTrades,
        long activeVenues,
        List<VenueBlock> venues,
        EnrichmentFreshnessSnapshot enrichmentFreshness
    )
    {
    }

    public record VenueBlock(
        String venue,
        String mode,
        boolean credentialsConfigured,
        boolean apiKeyLoaded,
        boolean secretKeyLoaded,
        boolean passphraseLoaded,
        boolean credentialsRequired,
        boolean modeOverridden,
        com.crypto.funding.domain.venue.VenueConnectionStatus connectionStatus,
        String connectionMessage,
        long activeInstrumentCount,
        java.time.Instant lastSyncedAt,
        java.time.Instant lastCheckedAt,
        Long averageRequestTimeMs,
        Long requests,
        Double enrichmentCoveragePct
    )
    {
    }

    private static final Set<ArmedTradeState> ACTIVE_TRADE_STATES = EnumSet.of(
        ArmedTradeState.ARMED,
        ArmedTradeState.ENTRY_PENDING,
        ArmedTradeState.ENTRY_ATTEMPTED,
        ArmedTradeState.OPEN,
        ArmedTradeState.EXIT_PENDING
    );

    private final SignalCandidateJpaRepository signalCandidateRepository;
    private final FundingEventJpaRepository fundingEventRepository;
    private final ArmedTradeJpaRepository armedTradeRepository;
    private final LiquidityAssessmentJpaRepository liquidityAssessmentRepository;
    private final VenueDiagnosticsService venueDiagnosticsService;
    private final FundingEventLifecycleService fundingEventLifecycleService;
    private final VenueProfileService venueProfileService;

    public MonitorOverviewService(
        SignalCandidateJpaRepository signalCandidateRepository,
        FundingEventJpaRepository fundingEventRepository,
        ArmedTradeJpaRepository armedTradeRepository,
        LiquidityAssessmentJpaRepository liquidityAssessmentRepository,
        VenueDiagnosticsService venueDiagnosticsService,
        FundingEventLifecycleService fundingEventLifecycleService,
        VenueProfileService venueProfileService
    )
    {
        this.signalCandidateRepository = signalCandidateRepository;
        this.fundingEventRepository = fundingEventRepository;
        this.armedTradeRepository = armedTradeRepository;
        this.liquidityAssessmentRepository = liquidityAssessmentRepository;
        this.venueDiagnosticsService = venueDiagnosticsService;
        this.fundingEventLifecycleService = fundingEventLifecycleService;
        this.venueProfileService = venueProfileService;
    }

    @Transactional
    public Overview load()
    {
        fundingEventLifecycleService.expirePastEvents();
        long pendingCandidates = signalCandidateRepository.findAll()
                                                          .stream()
                                                          .filter( candidate -> candidate.getStatus() == SignalCandidateStatus.NEW
                                                                               || candidate.getStatus() == SignalCandidateStatus.NORMALIZED )
                                                          .count();
        java.util.Set<Long> activeFundingEventIds = fundingEventRepository.findAll()
                                                                          .stream()
                                                                          .filter( event -> event.getStatus() == FundingEventStatus.DISCOVERED
                                                                                         || event.getStatus() == FundingEventStatus.ARMED )
                                                                          .map( com.crypto.funding.infrastructure.persistence.model.FundingEventEntity::getId )
                                                                          .collect( java.util.stream.Collectors.toSet() );
        long fundingEvents = activeFundingEventIds.size();
        long discoveredEvents = fundingEventRepository.findAll()
                                                      .stream()
                                                      .filter( event -> event.getStatus() == FundingEventStatus.DISCOVERED )
                                                      .count();
        long armedTrades = armedTradeRepository.findAll()
                                               .stream()
                                               .filter( trade -> activeFundingEventIds.contains( trade.getFundingEventId() ) )
                                               .count();

        List<VenueDiagnosticsService.VenueSummary> venueSummaries = venueDiagnosticsService.listVenues();
        Map<String, VenueRequestTimingService.Snapshot> latestTimings = venueDiagnosticsService.listTimings()
                                                                                               .stream()
                                                                                               .collect( Collectors.toMap(
                                                                                                   snapshot -> snapshot.venue().trim().toLowerCase( Locale.ROOT ),
                                                                                                   Function.identity(),
                                                                                                   ( left, right ) -> {
                                                                                                       if( left.lastOccurredAt() == null )
                                                                                                       {
                                                                                                           return right;
                                                                                                       }
                                                                                                       if( right.lastOccurredAt() == null )
                                                                                                       {
                                                                                                           return left;
                                                                                                       }
                                                                                                       return left.lastOccurredAt().isAfter( right.lastOccurredAt() ) ? left : right;
                                                                                                   }
                                                                                               ) );

        Map<String, Long> totalArmedByVenue = armedTradeRepository.countArmedTradesByVenue( ArmedTradeState.ARMED )
                                                                   .stream()
                                                                   .collect( Collectors.toMap(
                                                                       row -> ( (String) row[0] ).trim().toLowerCase( Locale.ROOT ),
                                                                       row -> (Long) row[1]
                                                                   ) );
        Map<String, Long> enrichedArmedByVenue = liquidityAssessmentRepository.countEnrichedArmedTradesByVenue( ArmedTradeState.ARMED )
                                                                               .stream()
                                                                               .collect( Collectors.toMap(
                                                                                   row -> ( (String) row[0] ).trim().toLowerCase( Locale.ROOT ),
                                                                                   row -> (Long) row[1]
                                                                               ) );

        List<VenueBlock> venues = venueSummaries.stream()
                                                .sorted( Comparator.comparing( VenueDiagnosticsService.VenueSummary::venue ) )
                                                .map( venue -> {
                                                    VenueRequestTimingService.Snapshot timing = latestTimings.get( venue.venue() );
                                                    String venueKey = venue.venue().trim().toLowerCase( Locale.ROOT );
                                                    Long total = totalArmedByVenue.get( venueKey );
                                                    Long enriched = enrichedArmedByVenue.getOrDefault( venueKey, 0L );
                                                    Double coveragePct = ( total == null || total == 0L )
                                                        ? null
                                                        : enriched * 100.0 / total;
                                                    return new VenueBlock(
                                                        venue.venue(),
                                                        venue.configuredMode(),
                                                        venue.credentialsConfigured(),
                                                        venue.apiKeyLoaded(),
                                                        venue.secretKeyLoaded(),
                                                        venue.passphraseLoaded(),
                                                        venue.credentialsRequired(),
                                                        venue.modeOverridden(),
                                                        venue.connectionStatus(),
                                                        venue.connectionMessage(),
                                                        venue.activeInstrumentCount(),
                                                        venue.lastSyncedAt(),
                                                        venue.lastCheckedAt(),
                                                        timing == null ? null : timing.averageDurationMs(),
                                                        timing == null ? null : timing.requests(),
                                                        coveragePct
                                                    );
                                                } )
                                                .toList();

        long activeVenues = venueSummaries.stream().filter( VenueDiagnosticsService.VenueSummary::enabledForMetadata ).count();

        VenueProfileService.GlobalAccessProfile globalAccessProfile = venueProfileService.getGlobalAccessProfile();

        return new Overview(
            "2.0.0",
            globalAccessProfile.mode().propertyValue(),
            globalAccessProfile.modeOverridden(),
            pendingCandidates,
            fundingEvents,
            discoveredEvents,
            armedTrades,
            activeVenues,
            venues,
            computeEnrichmentFreshness()
        );
    }

    private EnrichmentFreshnessSnapshot computeEnrichmentFreshness()
    {
        List<ArmedTradeEntity> activeTrades = armedTradeRepository.findAllByStateIn( ACTIVE_TRADE_STATES );
        if( activeTrades.isEmpty() )
        {
            return new EnrichmentFreshnessSnapshot( null, 0L );
        }

        List<Long> activeTradeIds = activeTrades.stream()
                                                .map( ArmedTradeEntity::getId )
                                                .collect( Collectors.toList() );

        Set<Long> coveredTradeIds = liquidityAssessmentRepository.findCoveredTradeIds( activeTradeIds );

        long uncoveredCount = activeTrades.stream()
                                          .filter( t -> !coveredTradeIds.contains( t.getId() ) )
                                          .count();

        if( coveredTradeIds.isEmpty() )
        {
            return new EnrichmentFreshnessSnapshot( null, uncoveredCount );
        }

        Instant now = Instant.now();
        double avgSeconds = coveredTradeIds.stream()
                                           .mapToLong( tradeId -> liquidityAssessmentRepository
                                               .findLatestSampledAtByTradeId( tradeId )
                                               .map( sampledAt -> now.getEpochSecond() - sampledAt.getEpochSecond() )
                                               .orElse( 0L ) )
                                           .average()
                                           .orElse( 0.0 );

        return new EnrichmentFreshnessSnapshot( avgSeconds, uncoveredCount );
    }
}
