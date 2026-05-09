package com.crypto.funding.application.monitor;

import com.crypto.funding.MonitorApplication;
import com.crypto.funding.contract.engine.EngineExecutionRunResponse;
import com.crypto.funding.contract.engine.EngineExecutionTargetPhase;
import com.crypto.funding.domain.venue.InstrumentStatus;
import com.crypto.funding.infrastructure.persistence.model.InstrumentMetadataEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.InstrumentMetadataJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = MonitorApplication.class, properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:sqlite:./build/test-dev-run-service-transaction.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "trading.candidate-source.enabled=false",
    "trading.metadata.sync-on-startup=false",
    "trading.metadata.schedule-enabled=false",
    "trading.metadata.require-credentials-on-startup=false",
    "trading.venue-access.mode=testnet",
    "monitor.dev-test-tool.enabled=true"
})
class DevTestRunServiceTransactionTest
{
    @Autowired
    private DevTestRunService devTestRunService;

    @Autowired
    private InstrumentMetadataJpaRepository instrumentMetadataRepository;

    @Autowired
    private FundingEventJpaRepository fundingEventRepository;

    @Autowired
    private ArmedTradeJpaRepository armedTradeRepository;

    @MockitoBean
    private EngineControlService engineControlService;

    @BeforeEach
    void resetDatabase()
    {
        armedTradeRepository.deleteAll();
        fundingEventRepository.deleteAll();
        instrumentMetadataRepository.deleteAll();
    }

    @Test
    void callsTargetedEngineExecutionOutsideMonitorTransaction()
    {
        seedInstrument( "bybit", "BTC/USDT", "BTCUSDT" );
        Long armedTradeId = devTestRunService.create( "bybit", "BTC/USDT", BigDecimal.valueOf( 25 ) )
                                             .armedTradeId();
        AtomicBoolean transactionActiveDuringEngineCall = new AtomicBoolean( true );
        when( engineControlService.runTarget( anyLong(), eq( EngineExecutionTargetPhase.ENTRY ), eq( true ) ) )
            .thenAnswer( invocation -> {
                transactionActiveDuringEngineCall.set( TransactionSynchronizationManager.isActualTransactionActive() );
                return new EngineExecutionRunResponse(
                    Instant.parse( "2030-01-01T00:00:00Z" ),
                    Instant.parse( "2030-01-01T00:00:01Z" ),
                    true,
                    1,
                    1,
                    0,
                    List.of()
                );
            } );

        devTestRunService.runPhase( armedTradeId, EngineExecutionTargetPhase.ENTRY, null );

        assertThat( transactionActiveDuringEngineCall ).isFalse();
    }

    private void seedInstrument( String venue, String symbol, String venueSymbol )
    {
        InstrumentMetadataEntity entity = new InstrumentMetadataEntity();
        entity.setVenue( venue );
        entity.setCanonicalSymbol( symbol );
        entity.setVenueSymbol( venueSymbol );
        entity.setBaseAsset( symbol.substring( 0, symbol.indexOf( '/' ) ) );
        entity.setQuoteAsset( "USDT" );
        entity.setInstrumentType( "PERPETUAL" );
        entity.setStatus( InstrumentStatus.ACTIVE );
        entity.setMinOrderQty( BigDecimal.ONE );
        entity.setQtyStep( BigDecimal.ONE );
        entity.setMinNotionalValue( BigDecimal.valueOf( 5 ) );
        entity.setLastSyncedAt( Instant.parse( "2030-01-01T00:00:00Z" ) );
        instrumentMetadataRepository.save( entity );
    }
}
