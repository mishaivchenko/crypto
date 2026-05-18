package com.crypto.funding.application.liquidity;

import com.crypto.funding.application.port.VenueOrderBookPort;
import com.crypto.funding.config.LiquidityProperties;
import com.crypto.funding.domain.liquidity.LiquidityAssessment;
import com.crypto.funding.domain.liquidity.LiquidityCalculator;
import com.crypto.funding.domain.liquidity.LiquidityScore;
import com.crypto.funding.domain.liquidity.LiquidityThresholds;
import com.crypto.funding.domain.liquidity.OrderBookSnapshot;
import com.crypto.funding.domain.trade.TradeSide;
import com.crypto.funding.infrastructure.persistence.model.LiquidityAssessmentEntity;
import com.crypto.funding.infrastructure.persistence.repository.LiquidityAssessmentJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LiquidityAssessmentService
{
    private static final Logger log = LoggerFactory.getLogger( LiquidityAssessmentService.class );

    private final LiquidityAssessmentJpaRepository repository;
    private final Map<String, VenueOrderBookPort> orderBookPortsByVenue;
    private final LiquidityProperties properties;
    private final Clock clock;

    private final Counter assessmentCreatedCounter;
    private final Counter assessmentUntradableCounter;
    private final Timer assessmentTimer;

    @org.springframework.beans.factory.annotation.Autowired
    public LiquidityAssessmentService(
        LiquidityAssessmentJpaRepository repository,
        List<VenueOrderBookPort> orderBookPorts,
        LiquidityProperties properties,
        MeterRegistry meterRegistry
    )
    {
        this( repository, orderBookPorts, properties, meterRegistry, Clock.systemUTC() );
    }

    LiquidityAssessmentService(
        LiquidityAssessmentJpaRepository repository,
        List<VenueOrderBookPort> orderBookPorts,
        LiquidityProperties properties,
        MeterRegistry meterRegistry,
        Clock clock
    )
    {
        this.repository = repository;
        this.orderBookPortsByVenue = orderBookPorts.stream()
                                                   .collect( Collectors.toMap( VenueOrderBookPort::venue, Function.identity() ) );
        this.properties = properties;
        this.clock = clock;
        this.assessmentCreatedCounter = meterRegistry.counter( "liquidity.assessment.created" );
        this.assessmentUntradableCounter = meterRegistry.counter( "liquidity.assessment.untradable" );
        this.assessmentTimer = meterRegistry.timer( "liquidity.assessment.duration" );
    }

    @Transactional
    public LiquidityAssessment assess( String venue, String venueSymbol, Long tradeId )
    {
        return assessmentTimer.record( () -> doAssess( venue, venueSymbol, tradeId ) );
    }

    private LiquidityAssessment doAssess( String venue, String venueSymbol, Long tradeId )
    {
        VenueOrderBookPort port = orderBookPortsByVenue.get( venue );
        if( port == null )
        {
            throw new IllegalArgumentException( "No order book adapter for venue: " + venue );
        }

        OrderBookSnapshot snapshot;
        try
        {
            snapshot = port.fetchOrderBook( venueSymbol, properties.getOrderBookDepth() );
        }
        catch( Exception e )
        {
            throw new RuntimeException( "Failed to fetch order book for " + venue + "/" + venueSymbol, e );
        }

        LiquidityThresholds thresholds = new LiquidityThresholds(
            properties.getMinTradableNotional(),
            properties.getThinNotional(),
            properties.getMediumNotional(),
            properties.getGoodNotional(),
            properties.getExcellentNotional()
        );

        Instant expiresAt = snapshot.sampledAt().plusMillis( properties.getTtlMs() );
        LiquidityAssessment assessment = LiquidityCalculator.assess(
            snapshot,
            TradeSide.SHORT,
            properties.getMaxSlippageBps(),
            properties.getSafetyHaircut(),
            thresholds,
            expiresAt
        );

        LiquidityAssessment withTradeId = tradeId == null ? assessment : new LiquidityAssessment(
            assessment.id(),
            tradeId,
            assessment.venue(),
            assessment.symbol(),
            assessment.side(),
            assessment.bestBid(),
            assessment.bestAsk(),
            assessment.spreadBps(),
            assessment.maxSlippageBps(),
            assessment.entryBidDepthNotional(),
            assessment.exitAskDepthNotional(),
            assessment.roundTripSafeNotional(),
            assessment.safetyHaircut(),
            assessment.recommendedMaxOrderNotional(),
            assessment.score(),
            assessment.sampledAt(),
            assessment.expiresAt()
        );

        save( withTradeId );

        log.info(
            "liquidity.assessment venue={} symbol={} side={} spreadBps={} entryBidDepth={} exitAskDepth={} roundTripSafe={} recommendedMax={} score={} sampledAt={}",
            withTradeId.venue(),
            withTradeId.symbol(),
            withTradeId.side(),
            withTradeId.spreadBps(),
            withTradeId.entryBidDepthNotional(),
            withTradeId.exitAskDepthNotional(),
            withTradeId.roundTripSafeNotional(),
            withTradeId.recommendedMaxOrderNotional(),
            withTradeId.score(),
            withTradeId.sampledAt()
        );

        assessmentCreatedCounter.increment();
        if( withTradeId.score() == LiquidityScore.UNTRADABLE )
        {
            assessmentUntradableCounter.increment();
        }

        return withTradeId;
    }

    @Transactional(readOnly = true)
    public Optional<LiquidityAssessment> findLatestForTrade( Long tradeId )
    {
        return repository.findFirstByTradeIdOrderBySampledAtDesc( tradeId )
                         .map( this::toDomain );
    }

    @Transactional(readOnly = true)
    public Optional<LiquidityAssessment> findByAssessmentId( String assessmentId )
    {
        return repository.findByAssessmentId( assessmentId )
                         .map( this::toDomain );
    }

    private void save( LiquidityAssessment assessment )
    {
        LiquidityAssessmentEntity entity = new LiquidityAssessmentEntity();
        entity.setAssessmentId( assessment.id() );
        entity.setTradeId( assessment.tradeId() );
        entity.setVenue( assessment.venue() );
        entity.setSymbol( assessment.symbol() );
        entity.setSide( assessment.side() );
        entity.setBestBid( assessment.bestBid() );
        entity.setBestAsk( assessment.bestAsk() );
        entity.setSpreadBps( assessment.spreadBps() );
        entity.setMaxSlippageBps( assessment.maxSlippageBps() );
        entity.setEntryBidDepthNotional( assessment.entryBidDepthNotional() );
        entity.setExitAskDepthNotional( assessment.exitAskDepthNotional() );
        entity.setRoundTripSafeNotional( assessment.roundTripSafeNotional() );
        entity.setSafetyHaircut( assessment.safetyHaircut() );
        entity.setRecommendedMaxOrderNotional( assessment.recommendedMaxOrderNotional() );
        entity.setScore( assessment.score() );
        entity.setSampledAt( assessment.sampledAt() );
        entity.setExpiresAt( assessment.expiresAt() );
        repository.save( entity );
    }

    private LiquidityAssessment toDomain( LiquidityAssessmentEntity entity )
    {
        return new LiquidityAssessment(
            entity.getAssessmentId(),
            entity.getTradeId(),
            entity.getVenue(),
            entity.getSymbol(),
            entity.getSide(),
            entity.getBestBid(),
            entity.getBestAsk(),
            entity.getSpreadBps(),
            entity.getMaxSlippageBps(),
            entity.getEntryBidDepthNotional(),
            entity.getExitAskDepthNotional(),
            entity.getRoundTripSafeNotional(),
            entity.getSafetyHaircut(),
            entity.getRecommendedMaxOrderNotional(),
            entity.getScore(),
            entity.getSampledAt(),
            entity.getExpiresAt()
        );
    }

    public boolean isExpired( LiquidityAssessment assessment )
    {
        if( assessment == null || assessment.expiresAt() == null )
        {
            return true;
        }
        return Instant.now( clock ).isAfter( assessment.expiresAt() );
    }

    public BigDecimal effectiveMaxOrderNotional( LiquidityAssessment assessment, BigDecimal plannedNotional )
    {
        if( assessment == null || assessment.recommendedMaxOrderNotional() == null )
        {
            return plannedNotional;
        }
        if( plannedNotional == null )
        {
            return assessment.recommendedMaxOrderNotional();
        }
        return plannedNotional.min( assessment.recommendedMaxOrderNotional() );
    }
}
