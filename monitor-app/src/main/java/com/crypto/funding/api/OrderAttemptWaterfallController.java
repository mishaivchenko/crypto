package com.crypto.funding.api;

import com.crypto.funding.domain.execution.OrderAttemptStatus;
import com.crypto.funding.infrastructure.persistence.model.OrderAttemptEntity;
import com.crypto.funding.infrastructure.persistence.repository.OrderAttemptJpaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class OrderAttemptWaterfallController
{
    private static final Set<String> REAL_EXCHANGE_TIMESTAMP_VENUES = Set.of( "gate", "bybit" );

    private final OrderAttemptJpaRepository orderAttemptJpaRepository;

    public OrderAttemptWaterfallController( OrderAttemptJpaRepository orderAttemptJpaRepository )
    {
        this.orderAttemptJpaRepository = orderAttemptJpaRepository;
    }

    @GetMapping("/order-attempts/waterfall")
    public Map<String, Object> getWaterfall( @RequestParam String venue )
    {
        List<OrderAttemptEntity> attempts = orderAttemptJpaRepository
            .findTop20ByVenueAndStatusOrderByCreatedAtDesc( venue, OrderAttemptStatus.FILLED );

        boolean hasRealExchangeTimestamp = REAL_EXCHANGE_TIMESTAMP_VENUES.contains( venue.toLowerCase() );

        if ( attempts.isEmpty() )
        {
            return buildResponse( venue, 0, hasRealExchangeTimestamp,
                null, null, null, null,
                null, null, null, null,
                0L );
        }

        List<Long> waitList = new ArrayList<>();
        List<Long> engineList = new ArrayList<>();
        List<Long> httpList = new ArrayList<>();
        List<Long> exchangeList = new ArrayList<>();

        for ( OrderAttemptEntity a : attempts )
        {
            Instant targetEntryAt = a.getTargetEntryAt();
            Instant triggerAt = a.getTriggerAt();
            Instant submittedAt = a.getSubmittedAt();
            Long requestDurationMs = a.getRequestDurationMs();
            Instant exchangeTimestamp = a.getExchangeTimestamp();

            if ( targetEntryAt != null && triggerAt != null )
            {
                waitList.add( triggerAt.toEpochMilli() - targetEntryAt.toEpochMilli() );
            }
            if ( triggerAt != null && submittedAt != null )
            {
                engineList.add( submittedAt.toEpochMilli() - triggerAt.toEpochMilli() );
            }
            if ( requestDurationMs != null )
            {
                httpList.add( requestDurationMs );
            }
            if ( hasRealExchangeTimestamp && submittedAt != null && requestDurationMs != null
                && exchangeTimestamp != null )
            {
                long responseAt = submittedAt.toEpochMilli() + requestDurationMs;
                long exchangeMs = exchangeTimestamp.toEpochMilli() - responseAt;
                if ( exchangeMs >= 0 )
                {
                    exchangeList.add( exchangeMs );
                }
            }
        }

        Long avgWaitMs = avg( waitList );
        Long avgEngineMs = avg( engineList );
        Long avgHttpMs = avg( httpList );
        Long avgExchangeMs = hasRealExchangeTimestamp ? avg( exchangeList ) : null;

        Long p95WaitMs = p95( waitList );
        Long p95EngineMs = p95( engineList );
        Long p95HttpMs = p95( httpList );
        Long p95ExchangeMs = hasRealExchangeTimestamp ? p95( exchangeList ) : null;

        long totalAvgMs = safeAdd( avgWaitMs, safeAdd( avgEngineMs, safeAdd( avgHttpMs, avgExchangeMs ) ) );

        return buildResponse(
            venue,
            attempts.size(),
            hasRealExchangeTimestamp,
            avgWaitMs, avgEngineMs, avgHttpMs, avgExchangeMs,
            p95WaitMs, p95EngineMs, p95HttpMs, p95ExchangeMs,
            totalAvgMs
        );
    }

    private static Long avg( List<Long> values )
    {
        if ( values.isEmpty() )
        {
            return null;
        }
        long sum = 0;
        for ( Long v : values )
        {
            sum += v;
        }
        return sum / values.size();
    }

    private static Long p95( List<Long> values )
    {
        if ( values.isEmpty() )
        {
            return null;
        }
        List<Long> sorted = new ArrayList<>( values );
        sorted.sort( Long::compareTo );
        int idx = (int) Math.ceil( 0.95 * sorted.size() ) - 1;
        if ( idx < 0 )
        {
            idx = 0;
        }
        return sorted.get( idx );
    }

    private static long safeAdd( Long a, long b )
    {
        return ( a != null ? a : 0L ) + b;
    }

    private static long safeAdd( Long a, Long b )
    {
        return ( a != null ? a : 0L ) + ( b != null ? b : 0L );
    }

    private static Map<String, Object> buildResponse(
        String venue,
        int sampleSize,
        boolean hasRealExchangeTimestamp,
        Long avgWaitMs,
        Long avgEngineMs,
        Long avgHttpMs,
        Long avgExchangeMs,
        Long p95WaitMs,
        Long p95EngineMs,
        Long p95HttpMs,
        Long p95ExchangeMs,
        long totalAvgMs
    )
    {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put( "venue", venue );
        response.put( "sampleSize", sampleSize );
        response.put( "hasRealExchangeTimestamp", hasRealExchangeTimestamp );
        response.put( "avgWaitMs", avgWaitMs );
        response.put( "avgEngineMs", avgEngineMs );
        response.put( "avgHttpMs", avgHttpMs );
        response.put( "avgExchangeMs", avgExchangeMs );
        response.put( "p95WaitMs", p95WaitMs );
        response.put( "p95EngineMs", p95EngineMs );
        response.put( "p95HttpMs", p95HttpMs );
        response.put( "p95ExchangeMs", p95ExchangeMs );
        response.put( "totalAvgMs", totalAvgMs );
        return response;
    }
}
