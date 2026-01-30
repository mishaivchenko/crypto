package com.crypto.funding.persistence.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(
    name = "approved_funding",
    indexes = {
        @Index(name = "idx_funding_symbol", columnList = "symbol"),
        @Index(name = "idx_funding_next_time", columnList = "next_funding_at")
    }
)
public class ApprovedFundingEntity
{

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @Version
    private Long version;

    @Column( name = "symbol", nullable = false )
    private String symbol;

    @ElementCollection( fetch = FetchType.EAGER )
    @CollectionTable(
        name = "approved_funding_exchange",
        joinColumns = @JoinColumn( name = "funding_id" )
    )
    @Column( name = "exchange", nullable = false )
    private Set<String> exchanges = new HashSet<>();

    @Column( name = "usdt_amount", nullable = false, precision = 19, scale = 8 )
    private BigDecimal usdtAmount;

    @Column( name = "active", nullable = false )
    private boolean active = true;

    @Column( name = "executed", nullable = false )
    private boolean executed = false;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "next_funding_at", nullable = false)
    private Instant nextFundingAt;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate()
    {
        updatedAt = Instant.now();
    }

    protected ApprovedFundingEntity()
    {
    }

    public ApprovedFundingEntity(
        String symbol,
        Set<String> exchanges,
        BigDecimal usdtAmount,
        Instant nextFundingAt
    )
    {
        this.symbol = symbol;
        this.exchanges = new HashSet<>( exchanges );
        this.usdtAmount = usdtAmount;
        this.nextFundingAt = nextFundingAt;
    }

    @PrePersist
    void onCreate()
    {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId()
    {
        return id;
    }

    public long getVersion()
    {
        return version;
    }

    public String getSymbol()
    {
        return symbol;
    }

    public String getSymbolUnified()
    {
        return symbol.replace("/", "").trim().toUpperCase( Locale.ROOT);
    }

    public Set<String> getExchanges()
    {
        return exchanges;
    }

    /**
     * Hibernate expects to mutate the collection instance. Never assign immutable collections here.
     */
    public void setExchanges( Set<String> exchanges )
    {
        this.exchanges.clear();
        if( exchanges != null )
        {
            this.exchanges.addAll( exchanges );
        }
    }

    public BigDecimal getUsdtAmount()
    {
        return usdtAmount;
    }

    public void setUsdtAmount( BigDecimal usdtAmount )
    {
        this.usdtAmount = usdtAmount;
    }

    public Instant getNextFundingAt()
    {
        return nextFundingAt;
    }

    public void setNextFundingAt( Instant nextFundingAt )
    {
        this.nextFundingAt = nextFundingAt;
    }

    public boolean isActive()
    {
        return active;
    }

    public void setActive( boolean active )
    {
        this.active = active;
    }

    public boolean isExecuted()
    {
        return executed;
    }

    public void setExecuted( boolean executed )
    {
        this.executed = executed;
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

    // getters / setters — без фанатизма
    @Converter( autoApply = false )
    public static class InstantEpochMillisConverter implements AttributeConverter<Instant, Long>
    {
        @Override
        public Long convertToDatabaseColumn( Instant attribute )
        {
            return attribute == null ? null : attribute.toEpochMilli();
        }

        @Override
        public Instant convertToEntityAttribute( Long dbData )
        {
            return dbData == null ? null : Instant.ofEpochMilli( dbData );
        }
    }
}
