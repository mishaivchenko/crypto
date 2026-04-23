package com.crypto.funding.infrastructure.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "operator_account",
    indexes = {
        @Index(name = "idx_operator_account_username", columnList = "username", unique = true),
        @Index(name = "idx_operator_account_token_hash", columnList = "token_hash", unique = true)
    }
)
public class OperatorAccountEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "integer")
    private Long id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public Long getId()
    {
        return id;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername( String username )
    {
        this.username = username;
    }

    public String getTokenHash()
    {
        return tokenHash;
    }

    public void setTokenHash( String tokenHash )
    {
        this.tokenHash = tokenHash;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled( boolean enabled )
    {
        this.enabled = enabled;
    }
}
