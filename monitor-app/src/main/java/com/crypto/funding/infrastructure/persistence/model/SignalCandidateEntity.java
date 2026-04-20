package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.domain.candidate.ReviewDecision;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(
    name = "signal_candidate",
    indexes = {
        @Index(name = "idx_signal_candidate_detected_at", columnList = "detected_at"),
        @Index(name = "idx_signal_candidate_status", columnList = "status"),
        @Index(name = "idx_signal_candidate_source_msg", columnList = "source_type,source_chat_id,source_message_id"),
        @Index(name = "idx_signal_candidate_raw_detected", columnList = "source_type,raw_symbol,detected_at")
    }
)
public class SignalCandidateEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_chat_id")
    private Long sourceChatId;

    @Column(name = "source_message_id")
    private Long sourceMessageId;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "source_venue")
    private String sourceVenue;

    @Column(name = "raw_symbol", nullable = false)
    private String rawSymbol;

    @Column(name = "normalized_symbol")
    private String normalizedSymbol;

    @Column(name = "venue_hints")
    private String venueHintsCsv;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SignalCandidateStatus status = SignalCandidateStatus.NEW;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_decision")
    private ReviewDecision reviewDecision;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "normalization_failure_reason", columnDefinition = "TEXT")
    private String normalizationFailureReason;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "source_funding_time")
    private Instant sourceFundingTime;

    @Column(name = "source_funding_rate_pct", precision = 18, scale = 8)
    private BigDecimal sourceFundingRatePct;

    @Column(name = "funding_event_id")
    private Long fundingEventId;

    @PrePersist
    void beforePersist()
    {
        if( detectedAt == null )
        {
            detectedAt = Instant.now();
        }
    }

    public Long getId()
    {
        return id;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceType( String sourceType )
    {
        this.sourceType = sourceType;
    }

    public Long getSourceChatId()
    {
        return sourceChatId;
    }

    public void setSourceChatId( Long sourceChatId )
    {
        this.sourceChatId = sourceChatId;
    }

    public Long getSourceMessageId()
    {
        return sourceMessageId;
    }

    public void setSourceMessageId( Long sourceMessageId )
    {
        this.sourceMessageId = sourceMessageId;
    }

    public String getRawPayload()
    {
        return rawPayload;
    }

    public void setRawPayload( String rawPayload )
    {
        this.rawPayload = rawPayload;
    }

    public String getRawSymbol()
    {
        return rawSymbol;
    }

    public String getSourceVenue()
    {
        return sourceVenue;
    }

    public void setSourceVenue( String sourceVenue )
    {
        this.sourceVenue = sourceVenue;
    }

    public void setRawSymbol( String rawSymbol )
    {
        this.rawSymbol = rawSymbol;
    }

    public String getNormalizedSymbol()
    {
        return normalizedSymbol;
    }

    public void setNormalizedSymbol( String normalizedSymbol )
    {
        this.normalizedSymbol = normalizedSymbol;
    }

    public List<String> getVenueHints()
    {
        if( venueHintsCsv == null || venueHintsCsv.isBlank() )
        {
            return List.of();
        }
        return Arrays.stream( venueHintsCsv.split( "," ) )
                     .map( String::trim )
                     .filter( value -> !value.isEmpty() )
                     .toList();
    }

    public void setVenueHints( List<String> venueHints )
    {
        if( venueHints == null || venueHints.isEmpty() )
        {
            this.venueHintsCsv = null;
            return;
        }
        this.venueHintsCsv = venueHints.stream().distinct().sorted().collect( Collectors.joining( "," ) );
    }

    public Instant getDetectedAt()
    {
        return detectedAt;
    }

    public void setDetectedAt( Instant detectedAt )
    {
        this.detectedAt = detectedAt;
    }

    public SignalCandidateStatus getStatus()
    {
        return status;
    }

    public void setStatus( SignalCandidateStatus status )
    {
        this.status = status;
    }

    public Instant getReviewedAt()
    {
        return reviewedAt;
    }

    public void setReviewedAt( Instant reviewedAt )
    {
        this.reviewedAt = reviewedAt;
    }

    public ReviewDecision getReviewDecision()
    {
        return reviewDecision;
    }

    public void setReviewDecision( ReviewDecision reviewDecision )
    {
        this.reviewDecision = reviewDecision;
    }

    public String getReviewNotes()
    {
        return reviewNotes;
    }

    public void setReviewNotes( String reviewNotes )
    {
        this.reviewNotes = reviewNotes;
    }

    public String getNormalizationFailureReason()
    {
        return normalizationFailureReason;
    }

    public void setNormalizationFailureReason( String normalizationFailureReason )
    {
        this.normalizationFailureReason = normalizationFailureReason;
    }

    public Instant getSourceFundingTime()
    {
        return sourceFundingTime;
    }

    public void setSourceFundingTime( Instant sourceFundingTime )
    {
        this.sourceFundingTime = sourceFundingTime;
    }

    public BigDecimal getSourceFundingRatePct()
    {
        return sourceFundingRatePct;
    }

    public void setSourceFundingRatePct( BigDecimal sourceFundingRatePct )
    {
        this.sourceFundingRatePct = sourceFundingRatePct;
    }

    public Long getFundingEventId()
    {
        return fundingEventId;
    }

    public void setFundingEventId( Long fundingEventId )
    {
        this.fundingEventId = fundingEventId;
    }
}
