package com.crypto.funding.application.security;

import com.crypto.funding.application.port.CredentialCipherPort;
import com.crypto.funding.config.CredentialStorageProperties;
import com.crypto.funding.contract.engine.EngineVenueCredentials;
import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.infrastructure.persistence.model.OperatorExchangeCredentialEntity;
import com.crypto.funding.infrastructure.persistence.repository.OperatorExchangeCredentialJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperatorCredentialServiceResolveTest
{
    private final OperatorExchangeCredentialJpaRepository repository =
        mock( OperatorExchangeCredentialJpaRepository.class );
    private final CredentialCipherPort cipher = mock( CredentialCipherPort.class );
    private final CredentialStorageProperties storageProperties = enabledStorage();

    private final OperatorCredentialService service = new OperatorCredentialService(
        storageProperties,
        cipher,
        repository,
        List.of(),
        new MockEnvironment()
    );

    @Test
    void returnsDecryptedCredentialsWhenEntityExists()
    {
        OperatorExchangeCredentialEntity entity = entity( "encrypted-key", "encrypted-secret", "encrypted-pass" );
        when( repository.findFirstByVenueAndMode( "gate", VenueAccessMode.TESTNET ) ).thenReturn( Optional.of( entity ) );
        when( cipher.decrypt( "encrypted-key" ) ).thenReturn( "api-key" );
        when( cipher.decrypt( "encrypted-secret" ) ).thenReturn( "secret-key" );
        when( cipher.decrypt( "encrypted-pass" ) ).thenReturn( "passphrase" );

        Optional<EngineVenueCredentials> result = service.resolveDecryptedForEngine( "gate", VenueAccessMode.TESTNET );

        assertThat( result ).isPresent();
        assertThat( result.get().apiKey() ).isEqualTo( "api-key" );
        assertThat( result.get().secretKey() ).isEqualTo( "secret-key" );
        assertThat( result.get().passphrase() ).isEqualTo( "passphrase" );
    }

    @Test
    void returnsNullPassphraseWhenPassphraseCiphertextIsNull()
    {
        OperatorExchangeCredentialEntity entity = entity( "encrypted-key", "encrypted-secret", null );
        when( repository.findFirstByVenueAndMode( "gate", VenueAccessMode.TESTNET ) ).thenReturn( Optional.of( entity ) );
        when( cipher.decrypt( "encrypted-key" ) ).thenReturn( "api-key" );
        when( cipher.decrypt( "encrypted-secret" ) ).thenReturn( "secret-key" );

        Optional<EngineVenueCredentials> result = service.resolveDecryptedForEngine( "gate", VenueAccessMode.TESTNET );

        assertThat( result ).isPresent();
        assertThat( result.get().passphrase() ).isNull();
    }

    @Test
    void returnsEmptyWhenNoEntityFound()
    {
        when( repository.findFirstByVenueAndMode( "bybit", VenueAccessMode.TESTNET ) ).thenReturn( Optional.empty() );

        Optional<EngineVenueCredentials> result = service.resolveDecryptedForEngine( "bybit", VenueAccessMode.TESTNET );

        assertThat( result ).isEmpty();
    }

    @Test
    void returnsEmptyWhenApiKeyCiphertextIsNull()
    {
        OperatorExchangeCredentialEntity entity = entity( null, "encrypted-secret", null );
        when( repository.findFirstByVenueAndMode( "gate", VenueAccessMode.TESTNET ) ).thenReturn( Optional.of( entity ) );

        Optional<EngineVenueCredentials> result = service.resolveDecryptedForEngine( "gate", VenueAccessMode.TESTNET );

        assertThat( result ).isEmpty();
    }

    @Test
    void normalizesVenueNameBeforeLookup()
    {
        OperatorExchangeCredentialEntity entity = entity( "k", "s", null );
        when( repository.findFirstByVenueAndMode( "gate", VenueAccessMode.TESTNET ) ).thenReturn( Optional.of( entity ) );
        when( cipher.decrypt( "k" ) ).thenReturn( "api-key" );
        when( cipher.decrypt( "s" ) ).thenReturn( "secret" );

        Optional<EngineVenueCredentials> result = service.resolveDecryptedForEngine( " GATE ", VenueAccessMode.TESTNET );

        assertThat( result ).isPresent();
    }

    private static OperatorExchangeCredentialEntity entity( String apiKeyCiphertext, String secretCiphertext, String passphraseCiphertext )
    {
        OperatorExchangeCredentialEntity e = new OperatorExchangeCredentialEntity();
        e.setVenue( "gate" );
        e.setMode( VenueAccessMode.TESTNET );
        e.setApiKeyCiphertext( apiKeyCiphertext );
        e.setSecretKeyCiphertext( secretCiphertext );
        e.setPassphraseCiphertext( passphraseCiphertext );
        return e;
    }

    private static CredentialStorageProperties enabledStorage()
    {
        CredentialStorageProperties p = new CredentialStorageProperties();
        p.setEnabled( true );
        p.setMasterKeyBase64( "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=" );
        return p;
    }
}
