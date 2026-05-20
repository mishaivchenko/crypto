package com.crypto.funding.application.ai;

import com.crypto.funding.application.candidate.SignalCandidateQueryService;
import com.crypto.funding.application.liquidity.LiquidityAssessmentService;
import com.crypto.funding.config.DeepSeekProperties;
import com.crypto.funding.domain.ai.AiSignalAdvice;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.liquidity.LiquidityAssessment;
import com.crypto.funding.infrastructure.ai.DeepSeekClient;
import com.crypto.funding.infrastructure.persistence.model.AiSignalAdviceEntity;
import com.crypto.funding.infrastructure.persistence.repository.AiSignalAdviceJpaRepository;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class AiSignalAdvisorService
{
    private static final Logger log = LoggerFactory.getLogger( AiSignalAdvisorService.class );

    private final DeepSeekProperties deepSeekProperties;
    private final DeepSeekClient deepSeekClient;
    private final SignalCandidateQueryService candidateQueryService;
    private final LiquidityAssessmentService liquidityAssessmentService;
    private final VenueRequestTimingService venueRequestTimingService;
    private final AiSignalAdviceJpaRepository adviceRepository;

    public AiSignalAdvisorService(
        DeepSeekProperties deepSeekProperties,
        DeepSeekClient deepSeekClient,
        SignalCandidateQueryService candidateQueryService,
        LiquidityAssessmentService liquidityAssessmentService,
        VenueRequestTimingService venueRequestTimingService,
        AiSignalAdviceJpaRepository adviceRepository
    )
    {
        this.deepSeekProperties = deepSeekProperties;
        this.deepSeekClient = deepSeekClient;
        this.candidateQueryService = candidateQueryService;
        this.liquidityAssessmentService = liquidityAssessmentService;
        this.venueRequestTimingService = venueRequestTimingService;
        this.adviceRepository = adviceRepository;
    }

    @Async
    public void analyzeAsync( Long candidateId )
    {
        if( !deepSeekProperties.isEnabled() )
        {
            return;
        }
        try
        {
            analyze( candidateId );
        }
        catch( Exception e )
        {
            log.warn( "AI signal analysis failed for candidate {}: {}", candidateId, e.getMessage() );
        }
    }

    @Transactional
    public AiSignalAdvice analyze( Long candidateId )
    {
        SignalCandidate candidate = candidateQueryService.getCandidate( candidateId );

        // Use already-stored liquidity to avoid an external API call inside the transaction.
        // assessAsync() runs independently on ingest; if not yet available the prompt notes it.
        Optional<LiquidityAssessment> liquidityOpt = liquidityAssessmentService.findLatestForCandidate( candidateId );

        String venue = resolveVenue( candidate );
        VenueRequestTimingService.Snapshot latency = venue != null
            ? venueRequestTimingService.snapshot( venue, "order_book" )
            : null;

        String prompt = buildPrompt( candidate, liquidityOpt.orElse( null ), latency );

        DeepSeekClient.AdviceResult result = deepSeekClient.analyze( prompt );

        AiSignalAdviceEntity entity = new AiSignalAdviceEntity();
        entity.setSignalCandidateId( candidateId );
        entity.setRecommendation( result.recommendation() );
        entity.setConfidence( result.confidence() );
        entity.setReasoning( result.reasoning() );
        entity.setModelUsed( result.modelUsed() );
        entity.setPromptTokens( result.promptTokens() );
        entity.setCompletionTokens( result.completionTokens() );
        entity.setAnalyzedAt( Instant.now() );
        AiSignalAdviceEntity saved = adviceRepository.save( entity );

        log.info( "AI advice for candidate {}: {} (confidence={:.2f})", candidateId, result.recommendation(), result.confidence() );

        return toDomain( saved );
    }

    @Transactional(readOnly = true)
    public Optional<AiSignalAdvice> findLatest( Long candidateId )
    {
        return adviceRepository.findFirstBySignalCandidateIdOrderByAnalyzedAtDesc( candidateId )
                               .map( this::toDomain );
    }

    private String buildPrompt( SignalCandidate candidate, LiquidityAssessment liquidity, VenueRequestTimingService.Snapshot latency )
    {
        long minutesUntilFunding = candidate.sourceFundingTime() != null
            ? java.time.Duration.between( Instant.now(), candidate.sourceFundingTime() ).toMinutes()
            : -1;

        StringBuilder sb = new StringBuilder();
        sb.append( "Ты опытный крипто-трейдер, специализирующийся на арбитраже финансирования.\n" );
        sb.append( "Проанализируй сигнал и дай рекомендацию.\n\n" );
        sb.append( "Символ: " ).append( candidate.normalizedSymbol() ).append( "\n" );
        sb.append( "Биржа: " ).append( resolveVenue( candidate ) != null ? resolveVenue( candidate ) : "неизвестно" ).append( "\n" );

        if( candidate.sourceFundingRatePct() != null )
        {
            sb.append( "Ставка финансирования: " ).append( candidate.sourceFundingRatePct() ).append( "%\n" );
        }
        if( minutesUntilFunding >= 0 )
        {
            sb.append( "До события финансирования: " ).append( minutesUntilFunding ).append( " мин\n" );
        }

        if( liquidity != null )
        {
            sb.append( "Ликвидность: " ).append( liquidity.score() );
            if( liquidity.spreadBps() != null )
            {
                sb.append( " (спред: " ).append( liquidity.spreadBps() ).append( " bps" );
            }
            if( liquidity.roundTripSafeNotional() != null )
            {
                sb.append( ", безопасный объём: $" ).append( liquidity.roundTripSafeNotional().setScale( 0, java.math.RoundingMode.HALF_UP ) );
            }
            sb.append( ")\n" );
        }
        else
        {
            sb.append( "Ликвидность: данные недоступны\n" );
        }

        if( latency != null && latency.p50DurationMs() != null )
        {
            sb.append( "Задержка venue p50: " ).append( latency.p50DurationMs() ).append( " ms" );
            if( latency.p95DurationMs() != null )
            {
                sb.append( ", p95: " ).append( latency.p95DurationMs() ).append( " ms" );
            }
            sb.append( "\n" );
        }

        sb.append( "\nОтветь СТРОГО в формате JSON (без markdown):\n" );
        sb.append( "{\"recommendation\":\"GO\",\"confidence\":0.8,\"reasoning\":\"...\"}\n" );
        sb.append( "Где recommendation — одно из: GO, WATCH, PASS\n" );
        sb.append( "confidence — число от 0.0 до 1.0\n" );
        sb.append( "reasoning — 2-3 предложения на русском языке\n" );

        return sb.toString();
    }

    private String resolveVenue( SignalCandidate candidate )
    {
        if( candidate.sourceVenue() != null && !candidate.sourceVenue().isBlank() )
        {
            return candidate.sourceVenue();
        }
        var hints = candidate.venueHints();
        if( hints != null && !hints.isEmpty() )
        {
            return hints.get( 0 );
        }
        return null;
    }

    private AiSignalAdvice toDomain( AiSignalAdviceEntity entity )
    {
        return new AiSignalAdvice(
            entity.getId(),
            entity.getSignalCandidateId(),
            entity.getRecommendation(),
            entity.getConfidence(),
            entity.getReasoning(),
            entity.getModelUsed(),
            entity.getPromptTokens(),
            entity.getCompletionTokens(),
            entity.getAnalyzedAt(),
            entity.getCreatedAt()
        );
    }
}
