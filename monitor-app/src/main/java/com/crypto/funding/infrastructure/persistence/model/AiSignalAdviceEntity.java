package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.domain.ai.AiRecommendation;
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
    name = "ai_signal_advice",
    indexes = {
        @Index(name = "idx_ai_advice_signal_candidate_id", columnList = "signal_candidate_id")
    }
)
public class AiSignalAdviceEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "integer")
    private Long id;

    @Column(name = "signal_candidate_id", nullable = false)
    private Long signalCandidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", nullable = false)
    private AiRecommendation recommendation;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    public Long getId()
    {
        return id;
    }

    public Long getSignalCandidateId()
    {
        return signalCandidateId;
    }

    public void setSignalCandidateId( Long signalCandidateId )
    {
        this.signalCandidateId = signalCandidateId;
    }

    public AiRecommendation getRecommendation()
    {
        return recommendation;
    }

    public void setRecommendation( AiRecommendation recommendation )
    {
        this.recommendation = recommendation;
    }

    public double getConfidence()
    {
        return confidence;
    }

    public void setConfidence( double confidence )
    {
        this.confidence = confidence;
    }

    public String getReasoning()
    {
        return reasoning;
    }

    public void setReasoning( String reasoning )
    {
        this.reasoning = reasoning;
    }

    public String getModelUsed()
    {
        return modelUsed;
    }

    public void setModelUsed( String modelUsed )
    {
        this.modelUsed = modelUsed;
    }

    public Integer getPromptTokens()
    {
        return promptTokens;
    }

    public void setPromptTokens( Integer promptTokens )
    {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens()
    {
        return completionTokens;
    }

    public void setCompletionTokens( Integer completionTokens )
    {
        this.completionTokens = completionTokens;
    }

    public Instant getAnalyzedAt()
    {
        return analyzedAt;
    }

    public void setAnalyzedAt( Instant analyzedAt )
    {
        this.analyzedAt = analyzedAt;
    }
}
