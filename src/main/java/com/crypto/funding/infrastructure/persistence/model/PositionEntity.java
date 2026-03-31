package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.domain.trade.PositionState;
import com.crypto.funding.domain.trade.TradeSide;
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
    name = "trade_position",
    indexes = {
        @Index(name = "idx_trade_position_trade_id", columnList = "armed_trade_id"),
        @Index(name = "idx_trade_position_state", columnList = "state")
    }
)
public class PositionEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "armed_trade_id", nullable = false)
    private Long armedTradeId;

    @Column(name = "venue", nullable = false)
    private String venue;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false)
    private TradeSide side;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "entry_price", precision = 19, scale = 8)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", precision = 19, scale = 8)
    private BigDecimal exitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private PositionState state = PositionState.PENDING_OPEN;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "opened_at")
    private Instant openedAt;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "closed_at")
    private Instant closedAt;

    public Long getId()
    {
        return id;
    }

    public Long getArmedTradeId()
    {
        return armedTradeId;
    }

    public void setArmedTradeId( Long armedTradeId )
    {
        this.armedTradeId = armedTradeId;
    }

    public String getVenue()
    {
        return venue;
    }

    public void setVenue( String venue )
    {
        this.venue = venue;
    }

    public String getSymbol()
    {
        return symbol;
    }

    public void setSymbol( String symbol )
    {
        this.symbol = symbol;
    }

    public TradeSide getSide()
    {
        return side;
    }

    public void setSide( TradeSide side )
    {
        this.side = side;
    }

    public BigDecimal getQuantity()
    {
        return quantity;
    }

    public void setQuantity( BigDecimal quantity )
    {
        this.quantity = quantity;
    }

    public BigDecimal getEntryPrice()
    {
        return entryPrice;
    }

    public void setEntryPrice( BigDecimal entryPrice )
    {
        this.entryPrice = entryPrice;
    }

    public BigDecimal getExitPrice()
    {
        return exitPrice;
    }

    public void setExitPrice( BigDecimal exitPrice )
    {
        this.exitPrice = exitPrice;
    }

    public PositionState getState()
    {
        return state;
    }

    public void setState( PositionState state )
    {
        this.state = state;
    }

    public Instant getOpenedAt()
    {
        return openedAt;
    }

    public void setOpenedAt( Instant openedAt )
    {
        this.openedAt = openedAt;
    }

    public Instant getClosedAt()
    {
        return closedAt;
    }

    public void setClosedAt( Instant closedAt )
    {
        this.closedAt = closedAt;
    }
}
