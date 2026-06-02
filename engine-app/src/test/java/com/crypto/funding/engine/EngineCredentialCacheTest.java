package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineVenueCredentials;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineCredentialCacheTest
{
    private final EnginePlanClient planClient = Mockito.mock( EnginePlanClient.class );

    // REQ: ENG-CACHE-001
    @Test
    void loadsAndCachesCredentialsFromMonitor()
    {
        EngineProperties props = propertiesFor( "gate", "testnet" );
        EngineVenueCredentials creds = new EngineVenueCredentials( "key", "secret", null );
        when( planClient.fetchCredentials( "gate", "testnet" ) ).thenReturn( Optional.of( creds ) );
        EngineCredentialCache cache = new EngineCredentialCache( planClient, props );

        cache.loadOnStartup();

        assertThat( cache.get( "gate" ) ).contains( creds );
    }

    // REQ: ENG-CACHE-002
    @Test
    void logsWarningAndDoesNotThrowWhenCredentialsMissingInMonitor()
    {
        EngineProperties props = propertiesFor( "okx", "testnet" );
        when( planClient.fetchCredentials( "okx", "testnet" ) ).thenReturn( Optional.empty() );
        EngineCredentialCache cache = new EngineCredentialCache( planClient, props );

        cache.loadOnStartup();

        assertThat( cache.get( "okx" ) ).isEmpty();
    }

    // REQ: ENG-CACHE-003
    @Test
    void getReturnsEmptyForUnknownVenueAndPresentForLoadedVenue()
    {
        EngineProperties props = propertiesFor( "gate", "testnet" );
        EngineVenueCredentials creds = new EngineVenueCredentials( "k", "s", null );
        when( planClient.fetchCredentials( "gate", "testnet" ) ).thenReturn( Optional.of( creds ) );
        EngineCredentialCache cache = new EngineCredentialCache( planClient, props );
        cache.loadOnStartup();

        assertThat( cache.get( "gate" ) ).isPresent();
        assertThat( cache.get( "unknown" ) ).isEmpty();
    }

    // REQ: ENG-CACHE-004
    @Test
    void loadOnStartupIteratesAllLiveEnabledVenuesWithConfiguredMode()
    {
        EngineProperties props = propertiesFor( "gate,okx", "testnet" );
        when( planClient.fetchCredentials( "gate", "testnet" ) ).thenReturn( Optional.of( new EngineVenueCredentials( "k1", "s1", null ) ) );
        when( planClient.fetchCredentials( "okx", "testnet" ) ).thenReturn( Optional.of( new EngineVenueCredentials( "k2", "s2", "pass" ) ) );
        EngineCredentialCache cache = new EngineCredentialCache( planClient, props );

        cache.loadOnStartup();

        verify( planClient ).fetchCredentials( "gate", "testnet" );
        verify( planClient ).fetchCredentials( "okx", "testnet" );
        assertThat( cache.get( "gate" ) ).isPresent();
        assertThat( cache.get( "okx" ) ).isPresent();
    }

    // REQ: ENG-CACHE-005
    @Test
    void retriesOnlyMissingVenuesUntilAllLoaded()
    {
        EngineProperties props = propertiesFor( "gate,okx", "testnet" );
        // gate fails first attempt, succeeds second
        when( planClient.fetchCredentials( "gate", "testnet" ) )
            .thenReturn( Optional.empty() )
            .thenReturn( Optional.of( new EngineVenueCredentials( "k1", "s1", null ) ) );
        when( planClient.fetchCredentials( "okx", "testnet" ) )
            .thenReturn( Optional.of( new EngineVenueCredentials( "k2", "s2", "pass" ) ) );
        EngineCredentialCache cache = new EngineCredentialCacheTestable( planClient, props );

        cache.loadOnStartup();

        assertThat( cache.get( "gate" ) ).isPresent();
        assertThat( cache.get( "okx" ) ).isPresent();
        // gate retried once, okx fetched once (already cached on first attempt)
        verify( planClient, times( 2 ) ).fetchCredentials( "gate", "testnet" );
        verify( planClient, times( 1 ) ).fetchCredentials( "okx", "testnet" );
    }

    // REQ: ENG-CACHE-006
    @Test
    void getReturnsEmptyWithoutBlockingWhenVenueNotInCache()
    {
        EngineProperties props = propertiesFor( "gate", "testnet" );
        EngineCredentialCache cache = new EngineCredentialCache( planClient, props );

        Optional<EngineVenueCredentials> result = cache.get( "gate" );

        assertThat( result ).isEmpty();
        Mockito.verifyNoInteractions( planClient );
    }

    private static EngineProperties propertiesFor( String venues, String mode )
    {
        EngineProperties props = new EngineProperties();
        props.setLiveEnabledVenues( venues );
        props.setTradingVenueAccessMode( mode );
        return props;
    }

    /** Subclass that skips Thread.sleep so retry tests run instantly. */
    private static class EngineCredentialCacheTestable extends EngineCredentialCache
    {
        EngineCredentialCacheTestable( EnginePlanClient planClient, EngineProperties properties )
        {
            super( planClient, properties );
        }

        @Override
        protected long retryIntervalMs()
        {
            return 0L;
        }
    }
}
