package com.crypto.funding.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineCredentialStatusControllerTest
{
    private final CredentialAwareExecutionPort executionPort = mock( CredentialAwareExecutionPort.class );
    private final EngineProperties properties = mock( EngineProperties.class );
    private final EngineCredentialCache credentialCache = mock( EngineCredentialCache.class );
    private final EngineCredentialStatusController controller =
        new EngineCredentialStatusController( executionPort, properties, credentialCache );

    @Test
    // REQ: ENG-CRED-CTL-001
    void returnsCredentialStatusForEachLiveEnabledVenue()
    {
        when( properties.liveEnabledVenues() ).thenReturn( List.of( "bybit", "gate" ) );
        when( executionPort.hasCredentials( "bybit" ) ).thenReturn( true );
        when( executionPort.hasCredentials( "gate" ) ).thenReturn( false );

        Map<String, Boolean> result = controller.status();

        assertThat( result ).containsEntry( "bybit", true )
                            .containsEntry( "gate", false )
                            .hasSize( 2 );
    }

    @Test
    // REQ: ENG-CRED-CTL-002
    void returnsEmptyMapWhenNoLiveVenuesConfigured()
    {
        when( properties.liveEnabledVenues() ).thenReturn( List.of() );

        Map<String, Boolean> result = controller.status();

        assertThat( result ).isEmpty();
    }

    @Test
    // REQ: ENG-CRED-CTL-003
    void reloadDelegatesAndReturnsUpdatedStatus()
    {
        when( properties.liveEnabledVenues() ).thenReturn( List.of( "okx" ) );
        when( executionPort.hasCredentials( "okx" ) ).thenReturn( true );

        Map<String, Boolean> result = controller.reload();

        verify( credentialCache ).loadOnStartup();
        assertThat( result ).containsEntry( "okx", true );
    }
}
