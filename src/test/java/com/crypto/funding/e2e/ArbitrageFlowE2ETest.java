package com.crypto.funding.e2e;

import com.crypto.funding.exchanges.ExchangeRestClient;
import com.crypto.funding.exchanges.AbstractRestClient;
import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import com.crypto.funding.scheduler.FundingSchedulerService;
import com.crypto.funding.scheduler.OrderExecutorService;
import com.crypto.funding.scheduler.NetworkLatencyService;
import com.crypto.funding.trading.OrderSide;
import com.crypto.funding.trading.OrderType;
import com.crypto.funding.trading.PlaceTestOrderCommand;
import com.crypto.funding.trading.TestOrderEngine;
import com.crypto.funding.trading.TestOrderResult;
import com.crypto.funding.watchlist.FundingInfo;
import com.crypto.funding.watchlist.SymbolRules;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Упрощённый E2E: проверяем связку репозиторий -> планировщик -> исполнение ордера.
 * Без реальных HTTP вызовов и без телеграма.
 */
class ArbitrageFlowE2ETest
{
    @Test
    void fullFlowExecutesOrderAndMarksExecuted() throws Exception
    {
        AtomicLong idSeq = new AtomicLong( 1 );
        InMemoryApprovedRepo repoState = new InMemoryApprovedRepo();

        ApprovedFundingRepository repo = Mockito.mock( ApprovedFundingRepository.class );
        when( repo.save( any() ) ).thenAnswer( inv -> {
            ApprovedFundingEntity e = inv.getArgument( 0 );
            if( e.getId() == null ) setId( e, idSeq.getAndIncrement() );
            repoState.save( e );
            return e;
        } );
        when( repo.findById( any() ) ).thenAnswer( inv -> repoState.findById( inv.getArgument(0) ) );
        when( repo.findFirstByActiveTrueAndExecutedFalseOrderByNextFundingAtAsc() )
            .thenAnswer( inv -> repoState.findFirst() );
        when( repo.findByActiveTrueAndExecutedFalseAndNextFundingAtBetween( any(), any() ) )
            .thenAnswer( inv -> repoState.findBetween( inv.getArgument( 0 ), inv.getArgument( 1 ) ) );

        Instant nextFunding = Instant.now().plusSeconds( 1 );
        ApprovedFundingEntity approved = new ApprovedFundingEntity(
            "BTC/USDT",
            Set.of( "bybit" ),
            new BigDecimal( "50" ),
            nextFunding
        );
        repo.save( approved );

        AtomicBoolean orderCalled = new AtomicBoolean( false );
        FakeRestClient fakeClient = new FakeRestClient( orderCalled );
        TestOrderEngine engine = new TestOrderEngine( List.of( fakeClient ) );
        OrderExecutorService executorService = new OrderExecutorService( engine, repo, List.of( fakeClient ), new NetworkLatencyService(), true );

        executorService.executeOnce( approved.getId() );

        assertThat( orderCalled ).isTrue();
        ApprovedFundingEntity stored = repoState.findById( approved.getId() ).orElseThrow();
        assertThat( stored.isExecuted() ).isTrue();
    }

    private static void setId( ApprovedFundingEntity entity, long id )
    {
        try
        {
            var f = ApprovedFundingEntity.class.getDeclaredField( "id" );
            f.setAccessible( true );
            f.set( entity, id );
        }
        catch( Exception e )
        {
            throw new RuntimeException( e );
        }
    }

    private static class FakeRestClient extends AbstractRestClient implements ExchangeRestClient, com.crypto.funding.trading.ExchangeTradingClient
    {
        private final AtomicBoolean called;

        FakeRestClient( AtomicBoolean called )
        {
            super( "http://local", "k", "s", 5000 );
            this.called = called;
        }

        @Override
        public String name()
        {
            return "bybit";
        }

        @Override
        public TestOrderResult createOrderResult( PlaceTestOrderCommand cmd, HttpResponse<String> response )
        {
            return new TestOrderResult( name(), "id-1", cmd.symbolUnified(), cmd.side(), cmd.type(),
                                        cmd.quantity(), cmd.price() == null ? BigDecimal.ONE : cmd.price(),
                                        "FILLED", System.currentTimeMillis() );
        }

        @Override
        public HttpRequest createHttpRequest( PlaceTestOrderCommand cmd )
        {
            return HttpRequest.newBuilder().uri( URI.create( "http://local" ) ).build();
        }

        @Override
        public String exchangeName()
        {
            return "bybit";
        }

        @Override
        public FundingInfo fetchFunding( String unifiedSymbol )
        {
            return new FundingInfo( name(), unifiedSymbol, 0.01, Instant.now().plusSeconds( 3600 ), 3600, BigDecimal.TEN );
        }

        @Override
        public SymbolRules fetchRules( String unifiedSymbol )
        {
            return new SymbolRules( new BigDecimal( "0.001" ), new BigDecimal( "0.001" ), null );
        }

        @Override
        public TestOrderResult placeTestOrder( PlaceTestOrderCommand cmd )
        {
            called.set( true );
            return new TestOrderResult( name(), "id-1", cmd.symbolUnified(), cmd.side(), cmd.type(),
                                        cmd.quantity(), cmd.price() == null ? BigDecimal.ONE : cmd.price(),
                                        "FILLED", System.currentTimeMillis() );
        }
    }

    private static class InMemoryApprovedRepo
    {
        private final List<ApprovedFundingEntity> store = new ArrayList<>();

        void save( ApprovedFundingEntity e )
        {
            store.removeIf( x -> Objects.equals( x.getId(), e.getId() ) );
            store.add( e );
        }

        Optional<ApprovedFundingEntity> findFirst()
        {
            return store.stream()
                        .filter( ApprovedFundingEntity::isActive )
                        .filter( e -> !e.isExecuted() )
                        .min( Comparator.comparing( ApprovedFundingEntity::getNextFundingAt ) );
        }

        Optional<ApprovedFundingEntity> findById( Long id )
        {
            return store.stream().filter( e -> Objects.equals( e.getId(), id ) ).findFirst();
        }

        List<ApprovedFundingEntity> findBetween( Instant from, Instant to )
        {
            List<ApprovedFundingEntity> res = new ArrayList<>();
            for( ApprovedFundingEntity e : store )
            {
                if( e.isActive() && !e.isExecuted()
                    && !e.getNextFundingAt().isBefore( from )
                    && !e.getNextFundingAt().isAfter( to ) )
                {
                    res.add( e );
                    e.setExecuted( true );
                }
            }
            return res;
        }
    }
}
