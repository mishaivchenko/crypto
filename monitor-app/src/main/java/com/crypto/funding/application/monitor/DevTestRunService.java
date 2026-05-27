package com.crypto.funding.application.monitor;

import com.crypto.funding.api.dto.DevTestRunExecutionResponse;
import com.crypto.funding.api.dto.DevTestRunOptionsResponse;
import com.crypto.funding.api.dto.DevTestRunResponse;
import com.crypto.funding.api.dto.DevTestRunSymbolOption;
import com.crypto.funding.api.dto.DevTestRunVenueOption;
import com.crypto.funding.api.dto.EngineRunOnceResponse;
import com.crypto.funding.api.dto.EngineRuntimeSettingsResponse;
import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.event.CreateFundingEventCommand;
import com.crypto.funding.application.event.FundingEventCommandService;
import com.crypto.funding.application.trade.ArmedTradeCommandService;
import com.crypto.funding.application.trade.CreateArmedTradeCommand;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.config.MonitorDevTestToolProperties;
import com.crypto.funding.contract.engine.EngineExecutionRunResponse;
import com.crypto.funding.contract.engine.EngineExecutionTargetPhase;
import com.crypto.funding.contract.engine.EngineRuntimeControlResponse;
import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.TradeArmSource;
import com.crypto.funding.domain.trade.TradeJournalActorType;
import com.crypto.funding.domain.trade.TradeSide;
import com.crypto.funding.domain.venue.InstrumentStatus;
import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.model.InstrumentMetadataEntity;
import com.crypto.funding.infrastructure.persistence.model.VenueTimingProfileEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.InstrumentMetadataJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.VenueTimingProfileJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DevTestRunService
{
    private static final String SOURCE_TYPE = "DEV_TEST_RUN";
    private static final Set<String> SUPPORTED_VENUES = Set.of( "bybit", "gate" );

    private final MonitorDevTestToolProperties properties;
    private final VenueProfileService venueProfileService;
    private final InstrumentMetadataJpaRepository instrumentMetadataRepository;
    private final FundingEventCommandService fundingEventCommandService;
    private final ArmedTradeCommandService armedTradeCommandService;
    private final FundingEventJpaRepository fundingEventRepository;
    private final ArmedTradeJpaRepository armedTradeRepository;
    private final VenueTimingProfileJpaRepository venueTimingProfileRepository;
    private final EngineControlService engineControlService;

    public DevTestRunService(
        MonitorDevTestToolProperties properties,
        VenueProfileService venueProfileService,
        InstrumentMetadataJpaRepository instrumentMetadataRepository,
        FundingEventCommandService fundingEventCommandService,
        ArmedTradeCommandService armedTradeCommandService,
        FundingEventJpaRepository fundingEventRepository,
        ArmedTradeJpaRepository armedTradeRepository,
        VenueTimingProfileJpaRepository venueTimingProfileRepository,
        EngineControlService engineControlService
    )
    {
        this.properties = properties;
        this.venueProfileService = venueProfileService;
        this.instrumentMetadataRepository = instrumentMetadataRepository;
        this.fundingEventCommandService = fundingEventCommandService;
        this.armedTradeCommandService = armedTradeCommandService;
        this.fundingEventRepository = fundingEventRepository;
        this.armedTradeRepository = armedTradeRepository;
        this.venueTimingProfileRepository = venueTimingProfileRepository;
        this.engineControlService = engineControlService;
    }

    @Transactional(readOnly = true)
    public DevTestRunOptionsResponse options()
    {
        VenueAccessMode mode = currentMode();
        EngineRuntimeControlResponse runtime = engineControlService.runtime();
        List<String> safetyIssues = safetyIssues( mode, runtime );
        Map<String, Boolean> credentialStatus = engineControlService.getEngineCredentialStatus();
        List<String> enabledVenues = properties.enabledVenues()
                                               .stream()
                                               .filter( SUPPORTED_VENUES::contains )
                                               .toList();
        for( String venue : enabledVenues )
        {
            if( !credentialStatus.getOrDefault( venue, false ) )
            {
                safetyIssues.add( "Engine credentials missing for " + venue.toUpperCase( Locale.ROOT )
                                  + ". Set ENGINE_CREDENTIALS_" + venue.toUpperCase( Locale.ROOT )
                                  + "_API_KEY and _SECRET_KEY env vars on the engine container." );
            }
        }
        return new DevTestRunOptionsResponse(
            isToolEnabled( mode ),
            mode,
            enabledVenues.stream()
                         .map( this::venueOption )
                         .toList(),
            toResponse( runtime ),
            safetyIssues
        );
    }

    @Transactional
    public DevTestRunResponse create( String rawVenue, String rawSymbol, BigDecimal notionalUsd )
    {
        VenueAccessMode mode = currentMode();
        assertToolEnabled( mode );

        String venue = normalizeVenue( rawVenue );
        String symbol = normalizeSymbol( rawSymbol );
        validateVenue( venue );
        validateNotional( notionalUsd );
        requireActiveInstrument( venue, symbol );

        Instant now = Instant.now();
        FundingEvent fundingEvent = fundingEventCommandService.create( new CreateFundingEventCommand(
            venue,
            symbol,
            now.plusSeconds( 300 ),
            BigDecimal.ZERO,
            SOURCE_TYPE,
            "dev-test-run:" + venue + ":" + symbol + ":" + now,
            null
        ) );
        ArmedTrade armedTrade = armedTradeCommandService.create(
            new CreateArmedTradeCommand(
                fundingEvent.id(),
                notionalUsd,
                TradeSide.SHORT,
                now.plusSeconds( 10 ),
                now.plusSeconds( 70 ),
                1,
                0L,
                0L,
                SOURCE_TYPE + " " + mode + " " + venue + " " + symbol,
                null,
                null
            ),
            TradeArmSource.DIRECT_TRADE_API,
            TradeJournalActorType.OPERATOR,
            "dev-test-run"
        );
        seedTestnetTimingMarker( mode, venue, symbol, now );

        return new DevTestRunResponse(
            fundingEvent.id(),
            armedTrade.id(),
            mode,
            venue,
            symbol,
            armedTrade.notionalUsd(),
            armedTrade.state().name()
        );
    }

    private void seedTestnetTimingMarker( VenueAccessMode mode, String venue, String symbol, Instant sampledAt )
    {
        if( mode != VenueAccessMode.TESTNET )
        {
            return;
        }
        VenueTimingProfileEntity timing = new VenueTimingProfileEntity();
        timing.setVenue( venue );
        timing.setSymbol( symbol );
        timing.setObservedLagMs( 0L );
        timing.setEntryLatencyMs( 0L );
        timing.setExitLatencyMs( 0L );
        timing.setSampledAt( sampledAt );
        timing.setNotes( "DEV_TEST_RUN testnet freshness marker; not a production latency proof." );
        venueTimingProfileRepository.save( timing );
    }

    public DevTestRunExecutionResponse runPhase( Long armedTradeId, EngineExecutionTargetPhase phase, String productionConfirm )
    {
        ArmedTradeEntity trade = armedTradeRepository.findById( armedTradeId )
                                                     .orElseThrow( () -> new ResourceNotFoundException(
                                                         "Подготовленная сделка не найдена: " + armedTradeId
                                                     ) );
        FundingEventEntity event = fundingEventRepository.findById( trade.getFundingEventId() )
                                                         .orElseThrow( () -> new ResourceNotFoundException(
                                                             "Событие фандинга не найдено: " + trade.getFundingEventId()
                                                         ) );
        if( !SOURCE_TYPE.equals( event.getSourceType() ) )
        {
            throw new DomainValidationException( "Only DEV_TEST_RUN trades can be executed through this dev tool." );
        }

        VenueAccessMode mode = currentMode();
        assertToolEnabled( mode );
        validateVenue( event.getVenue() );
        validateNotional( trade.getNotionalUsd() );
        if( mode == VenueAccessMode.PRODUCTION )
        {
            String required = event.getVenue() + " " + event.getSymbol() + " LIVE";
            if( !required.equals( productionConfirm ) )
            {
                throw new DomainValidationException( "Production dev test run requires confirmation: " + required );
            }
            List<String> productionIssues = productionExecutionIssues( engineControlService.runtime(), event.getVenue(), trade.getNotionalUsd() );
            if( !productionIssues.isEmpty() )
            {
                throw new DomainValidationException( "Production dev test run safety gate failed: " + String.join( "; ", productionIssues ) );
            }
        }

        EngineExecutionRunResponse response = engineControlService.runTarget( armedTradeId, phase, true );
        return new DevTestRunExecutionResponse(
            armedTradeId,
            phase,
            mode,
            new EngineRunOnceResponse(
                response.startedAt(),
                response.finishedAt(),
                response.force(),
                response.plansScanned(),
                response.attemptsSubmitted(),
                response.attemptsSkipped(),
                response.results()
            )
        );
    }

    private DevTestRunVenueOption venueOption( String venue )
    {
        List<DevTestRunSymbolOption> symbols = instrumentMetadataRepository.findAllByVenueOrderByCanonicalSymbolAsc( venue )
                                                                           .stream()
                                                                           .filter( instrument -> instrument.getStatus() == InstrumentStatus.ACTIVE )
                                                                           .map( instrument -> new DevTestRunSymbolOption(
                                                                               instrument.getCanonicalSymbol(),
                                                                               instrument.getVenueSymbol()
                                                                           ) )
                                                                           .toList();
        return new DevTestRunVenueOption( venue, SUPPORTED_VENUES.contains( venue ), symbols );
    }

    private InstrumentMetadataEntity requireActiveInstrument( String venue, String symbol )
    {
        return instrumentMetadataRepository.findByVenueAndCanonicalSymbolAndStatus( venue, symbol, InstrumentStatus.ACTIVE )
                                           .orElseThrow( () -> new DomainValidationException(
                                               "Dev test run symbol is not active on " + venue + ": " + symbol
                                           ) );
    }

    private void validateVenue( String venue )
    {
        if( !SUPPORTED_VENUES.contains( venue ) || !properties.enabledVenues().contains( venue ) )
        {
            throw new DomainValidationException( "Dev test run venue is not supported: " + venue );
        }
    }

    private void validateNotional( BigDecimal notionalUsd )
    {
        if( notionalUsd == null || notionalUsd.signum() <= 0 )
        {
            throw new DomainValidationException( "Dev test run notionalUsd must be positive." );
        }
        BigDecimal maxNotionalUsd = properties.getMaxNotionalUsd() == null ? BigDecimal.valueOf( 25 ) : properties.getMaxNotionalUsd();
        if( notionalUsd.compareTo( maxNotionalUsd ) > 0 )
        {
            throw new DomainValidationException( "Dev test run notionalUsd must be <= " + maxNotionalUsd + "." );
        }
    }

    private void assertToolEnabled( VenueAccessMode mode )
    {
        if( !isToolEnabled( mode ) )
        {
            throw new DomainValidationException( "Dev test run tool is disabled for production. Set MONITOR_DEV_TEST_TOOL_ENABLED=true." );
        }
    }

    private List<String> safetyIssues( VenueAccessMode mode, EngineRuntimeControlResponse runtime )
    {
        List<String> issues = new ArrayList<>();
        if( !isToolEnabled( mode ) )
        {
            issues.add( "Prod dev test tool is disabled." );
        }
        if( mode == VenueAccessMode.PRODUCTION )
        {
            if( !runtime.liveOrderEnabled() )
            {
                issues.add( "ENGINE_LIVE_ORDER_ENABLED is false." );
            }
            if( runtime.killSwitchEnabled() )
            {
                issues.add( "ENGINE_KILL_SWITCH_ENABLED is true." );
            }
        }
        return issues;
    }

    private List<String> productionExecutionIssues( EngineRuntimeControlResponse runtime, String venue, BigDecimal notionalUsd )
    {
        List<String> issues = new ArrayList<>();
        if( !runtime.liveOrderEnabled() )
        {
            issues.add( "ENGINE_LIVE_ORDER_ENABLED is false." );
        }
        if( runtime.killSwitchEnabled() )
        {
            issues.add( "ENGINE_KILL_SWITCH_ENABLED is true." );
        }
        List<String> enabledVenues = runtime.liveEnabledVenues() == null
                                     ? List.of()
                                     : runtime.liveEnabledVenues()
                                              .stream()
                                              .map( DevTestRunService::normalizeVenue )
                                              .toList();
        if( !enabledVenues.contains( normalizeVenue( venue ) ) )
        {
            issues.add( "Venue " + venue + " is not enabled in ENGINE_LIVE_ENABLED_VENUES." );
        }
        BigDecimal maxNotionalUsd = runtime.maxNotionalUsd() == null ? properties.getMaxNotionalUsd() : runtime.maxNotionalUsd();
        if( maxNotionalUsd != null && notionalUsd.compareTo( maxNotionalUsd ) > 0 )
        {
            issues.add( "Notional must be <= " + maxNotionalUsd + " USDT." );
        }
        return issues;
    }

    private boolean isToolEnabled( VenueAccessMode mode )
    {
        return mode != VenueAccessMode.PRODUCTION || properties.isEnabled();
    }

    private VenueAccessMode currentMode()
    {
        return venueProfileService.getGlobalAccessProfile().mode();
    }

    private static String normalizeVenue( String rawVenue )
    {
        if( rawVenue == null || rawVenue.isBlank() )
        {
            throw new DomainValidationException( "venue must not be blank" );
        }
        return rawVenue.trim().toLowerCase( Locale.ROOT );
    }

    private static String normalizeSymbol( String rawSymbol )
    {
        if( rawSymbol == null || rawSymbol.isBlank() )
        {
            throw new DomainValidationException( "symbol must not be blank" );
        }
        return rawSymbol.trim().toUpperCase( Locale.ROOT );
    }

    private static EngineRuntimeSettingsResponse toResponse( EngineRuntimeControlResponse response )
    {
        return new EngineRuntimeSettingsResponse(
            response.module(),
            response.version(),
            response.tradingVenueAccessMode(),
            response.liveOrderEnabled(),
            response.killSwitchEnabled(),
            response.liveEnabledVenues(),
            response.maxNotionalUsd(),
            response.executionLoopEnabled(),
            response.executionLoopIntervalMs(),
            response.minimumExecutionLoopIntervalMs(),
            response.runtimeUpdatedAt(),
            response.lastRunStartedAt(),
            response.lastRunFinishedAt(),
            response.lastRunForced(),
            response.lastPlansScanned(),
            response.lastAttemptsSubmitted(),
            response.lastAttemptsSkipped(),
            response.lastExecutionRunDurationMs(),
            response.lastForcedRunStartedAt(),
            response.lastForcedRunFinishedAt(),
            response.lastForcedPlansScanned(),
            response.lastForcedAttemptsSubmitted(),
            response.lastForcedAttemptsSkipped(),
            response.lastForcedRunDurationMs()
        );
    }
}
