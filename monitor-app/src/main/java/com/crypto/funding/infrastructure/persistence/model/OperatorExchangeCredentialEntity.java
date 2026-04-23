package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.domain.venue.VenueConnectionStatus;
import com.crypto.funding.infrastructure.persistence.converter.InstantEpochMillisConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
    name = "operator_exchange_credential",
    indexes = {
        @Index(name = "idx_operator_credential_unique", columnList = "operator_id,venue,mode", unique = true)
    }
)
public class OperatorExchangeCredentialEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "integer")
    private Long id;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "venue", nullable = false)
    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private VenueAccessMode mode;

    @Column(name = "api_key_ciphertext", columnDefinition = "TEXT")
    private String apiKeyCiphertext;

    @Column(name = "secret_key_ciphertext", columnDefinition = "TEXT")
    private String secretKeyCiphertext;

    @Column(name = "passphrase_ciphertext", columnDefinition = "TEXT")
    private String passphraseCiphertext;

    @Column(name = "api_key_mask")
    private String apiKeyMask;

    @Column(name = "secret_key_mask")
    private String secretKeyMask;

    @Column(name = "passphrase_mask")
    private String passphraseMask;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false)
    private VenueConnectionStatus connectionStatus = VenueConnectionStatus.NOT_CONNECTED;

    @Column(name = "connection_message", columnDefinition = "TEXT")
    private String connectionMessage;

    @Column(name = "last_connection_http_status")
    private Integer lastConnectionHttpStatus;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    public Long getId()
    {
        return id;
    }

    public Long getOperatorId()
    {
        return operatorId;
    }

    public void setOperatorId( Long operatorId )
    {
        this.operatorId = operatorId;
    }

    public String getVenue()
    {
        return venue;
    }

    public void setVenue( String venue )
    {
        this.venue = venue;
    }

    public VenueAccessMode getMode()
    {
        return mode;
    }

    public void setMode( VenueAccessMode mode )
    {
        this.mode = mode;
    }

    public String getApiKeyCiphertext()
    {
        return apiKeyCiphertext;
    }

    public void setApiKeyCiphertext( String apiKeyCiphertext )
    {
        this.apiKeyCiphertext = apiKeyCiphertext;
    }

    public String getSecretKeyCiphertext()
    {
        return secretKeyCiphertext;
    }

    public void setSecretKeyCiphertext( String secretKeyCiphertext )
    {
        this.secretKeyCiphertext = secretKeyCiphertext;
    }

    public String getPassphraseCiphertext()
    {
        return passphraseCiphertext;
    }

    public void setPassphraseCiphertext( String passphraseCiphertext )
    {
        this.passphraseCiphertext = passphraseCiphertext;
    }

    public String getApiKeyMask()
    {
        return apiKeyMask;
    }

    public void setApiKeyMask( String apiKeyMask )
    {
        this.apiKeyMask = apiKeyMask;
    }

    public String getSecretKeyMask()
    {
        return secretKeyMask;
    }

    public void setSecretKeyMask( String secretKeyMask )
    {
        this.secretKeyMask = secretKeyMask;
    }

    public String getPassphraseMask()
    {
        return passphraseMask;
    }

    public void setPassphraseMask( String passphraseMask )
    {
        this.passphraseMask = passphraseMask;
    }

    public VenueConnectionStatus getConnectionStatus()
    {
        return connectionStatus;
    }

    public void setConnectionStatus( VenueConnectionStatus connectionStatus )
    {
        this.connectionStatus = connectionStatus;
    }

    public String getConnectionMessage()
    {
        return connectionMessage;
    }

    public void setConnectionMessage( String connectionMessage )
    {
        this.connectionMessage = connectionMessage;
    }

    public Integer getLastConnectionHttpStatus()
    {
        return lastConnectionHttpStatus;
    }

    public void setLastConnectionHttpStatus( Integer lastConnectionHttpStatus )
    {
        this.lastConnectionHttpStatus = lastConnectionHttpStatus;
    }

    public Instant getLastCheckedAt()
    {
        return lastCheckedAt;
    }

    public void setLastCheckedAt( Instant lastCheckedAt )
    {
        this.lastCheckedAt = lastCheckedAt;
    }
}
