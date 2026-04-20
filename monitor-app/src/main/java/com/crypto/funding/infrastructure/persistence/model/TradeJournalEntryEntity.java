package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.domain.trade.TradeJournalActorType;
import com.crypto.funding.domain.trade.TradeJournalEntityType;
import com.crypto.funding.domain.trade.TradeJournalEventCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "trade_journal",
    indexes = {
        @Index(name = "idx_trade_journal_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_trade_journal_event_code", columnList = "event_code")
    }
)
public class TradeJournalEntryEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private TradeJournalEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_code", nullable = false)
    private TradeJournalEventCode eventCode;

    @Column(name = "old_state")
    private String oldState;

    @Column(name = "new_state")
    private String newState;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private TradeJournalActorType actorType;

    @Column(name = "actor_ref")
    private String actorRef;

    @Column(name = "note", length = 2000)
    private String note;

    public Long getId()
    {
        return id;
    }

    public TradeJournalEntityType getEntityType()
    {
        return entityType;
    }

    public void setEntityType( TradeJournalEntityType entityType )
    {
        this.entityType = entityType;
    }

    public Long getEntityId()
    {
        return entityId;
    }

    public void setEntityId( Long entityId )
    {
        this.entityId = entityId;
    }

    public TradeJournalEventCode getEventCode()
    {
        return eventCode;
    }

    public void setEventCode( TradeJournalEventCode eventCode )
    {
        this.eventCode = eventCode;
    }

    public String getOldState()
    {
        return oldState;
    }

    public void setOldState( String oldState )
    {
        this.oldState = oldState;
    }

    public String getNewState()
    {
        return newState;
    }

    public void setNewState( String newState )
    {
        this.newState = newState;
    }

    public TradeJournalActorType getActorType()
    {
        return actorType;
    }

    public void setActorType( TradeJournalActorType actorType )
    {
        this.actorType = actorType;
    }

    public String getActorRef()
    {
        return actorRef;
    }

    public void setActorRef( String actorRef )
    {
        this.actorRef = actorRef;
    }

    public String getNote()
    {
        return note;
    }

    public void setNote( String note )
    {
        this.note = note;
    }
}
