package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.infrastructure.persistence.converter.InstantEpochMillisConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;

@MappedSuperclass
public abstract class AuditableEntity
{
    @Version
    @Column(name = "version")
    private Long version;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate()
    {
        Instant now = Instant.now();
        if( createdAt == null )
        {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate()
    {
        updatedAt = Instant.now();
    }

    public Long getVersion()
    {
        return version;
    }

    public Instant getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt( Instant createdAt )
    {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt()
    {
        return updatedAt;
    }

    public void setUpdatedAt( Instant updatedAt )
    {
        this.updatedAt = updatedAt;
    }
}
