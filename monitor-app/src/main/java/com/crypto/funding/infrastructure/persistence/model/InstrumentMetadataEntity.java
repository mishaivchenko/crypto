package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.domain.venue.InstrumentStatus;
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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "instrument_metadata",
    indexes = {
        @Index(name = "idx_instrument_metadata_venue_symbol", columnList = "venue,canonical_symbol", unique = true),
        @Index(name = "idx_instrument_metadata_status", columnList = "status")
    }
)
public class InstrumentMetadataEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venue", nullable = false)
    private String venue;

    @Column(name = "canonical_symbol", nullable = false)
    private String canonicalSymbol;

    @Column(name = "venue_symbol", nullable = false)
    private String venueSymbol;

    @Column(name = "base_asset")
    private String baseAsset;

    @Column(name = "quote_asset")
    private String quoteAsset;

    @Column(name = "instrument_type")
    private String instrumentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InstrumentStatus status = InstrumentStatus.ACTIVE;

    @Column(name = "min_order_qty", precision = 19, scale = 8)
    private BigDecimal minOrderQty;

    @Column(name = "qty_step", precision = 19, scale = 8)
    private BigDecimal qtyStep;

    @Column(name = "min_notional_value", precision = 19, scale = 8)
    private BigDecimal minNotionalValue;

    @Column(name = "quantity_precision")
    private Integer quantityPrecision;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    public Long getId()
    {
        return id;
    }

    public String getVenue()
    {
        return venue;
    }

    public void setVenue( String venue )
    {
        this.venue = venue;
    }

    public String getCanonicalSymbol()
    {
        return canonicalSymbol;
    }

    public void setCanonicalSymbol( String canonicalSymbol )
    {
        this.canonicalSymbol = canonicalSymbol;
    }

    public String getVenueSymbol()
    {
        return venueSymbol;
    }

    public void setVenueSymbol( String venueSymbol )
    {
        this.venueSymbol = venueSymbol;
    }

    public String getBaseAsset()
    {
        return baseAsset;
    }

    public void setBaseAsset( String baseAsset )
    {
        this.baseAsset = baseAsset;
    }

    public String getQuoteAsset()
    {
        return quoteAsset;
    }

    public void setQuoteAsset( String quoteAsset )
    {
        this.quoteAsset = quoteAsset;
    }

    public String getInstrumentType()
    {
        return instrumentType;
    }

    public void setInstrumentType( String instrumentType )
    {
        this.instrumentType = instrumentType;
    }

    public InstrumentStatus getStatus()
    {
        return status;
    }

    public void setStatus( InstrumentStatus status )
    {
        this.status = status;
    }

    public BigDecimal getMinOrderQty()
    {
        return minOrderQty;
    }

    public void setMinOrderQty( BigDecimal minOrderQty )
    {
        this.minOrderQty = minOrderQty;
    }

    public BigDecimal getQtyStep()
    {
        return qtyStep;
    }

    public void setQtyStep( BigDecimal qtyStep )
    {
        this.qtyStep = qtyStep;
    }

    public BigDecimal getMinNotionalValue()
    {
        return minNotionalValue;
    }

    public void setMinNotionalValue( BigDecimal minNotionalValue )
    {
        this.minNotionalValue = minNotionalValue;
    }

    public Integer getQuantityPrecision()
    {
        return quantityPrecision;
    }

    public void setQuantityPrecision( Integer quantityPrecision )
    {
        this.quantityPrecision = quantityPrecision;
    }

    public Instant getLastSyncedAt()
    {
        return lastSyncedAt;
    }

    public void setLastSyncedAt( Instant lastSyncedAt )
    {
        this.lastSyncedAt = lastSyncedAt;
    }
}
