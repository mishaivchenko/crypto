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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private final AiAdvisorPerformanceService performanceService;

    public AiSignalAdvisorService(
        DeepSeekProperties deepSeekProperties,
        DeepSeekClient deepSeekClient,
        SignalCandidateQueryService candidateQueryService,
        LiquidityAssessmentService liquidityAssessmentService,
        VenueRequestTimingService venueRequestTimingService,
        AiSignalAdviceJpaRepository adviceRepository,
        AiAdvisorPerformanceService performanceService
    )
    {
        this.deepSeekProperties = deepSeekProperties;
        this.deepSeekClient = deepSeekClient;
        this.candidateQueryService = candidateQueryService;
        this.liquidityAssessmentService = liquidityAssessmentService;
        this.venueRequestTimingService = venueRequestTimingService;
        this.adviceRepository = adviceRepository;
        this.performanceService = performanceService;
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

    // No @Transactional here — deepSeekClient.analyze() is a network call that can take seconds.
    // Each collaborating service (candidateQueryService, adviceRepository.save) manages its own short transaction.
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

        AiAdvisorPerformanceService.PerformanceStats perfStats = performanceService.getPerformanceStats();

        String prompt = buildPrompt( candidate, liquidityOpt.orElse( null ), latency, perfStats );

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

    private String buildPrompt(
        SignalCandidate candidate,
        LiquidityAssessment liquidity,
        VenueRequestTimingService.Snapshot latency,
        AiAdvisorPerformanceService.PerformanceStats perfStats
    )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "Ты торговый советник для краткосрочной SHORT-стратегии на перпетуальных фьючерсах.\n\n" );
        sb.append( "СТРАТЕГИЯ (важно понять правильно):\n" );
        sb.append( "- Мы входим в SHORT за 1-5 секунд ДО момента списания фандинга.\n" );
        sb.append( "- Цель: поймать компенсирующее ценовое движение ВНИЗ в первые секунды после фандинга.\n" );
        sb.append( "- Когда крупный фандинг списывается с лонгов — многие лонги закрываются, цена резко падает.\n" );
        sb.append( "- Мы выходим из шорта через несколько секунд-минут, захватив это ценовое движение.\n" );
        sb.append( "- Мы НЕ держим позицию ради получения фандинга — мы ловим ценовое движение.\n\n" );
        sb.append( "Символ: " ).append( candidate.normalizedSymbol() ).append( "\n" );
        sb.append( "Биржа: " ).append( resolveVenue( candidate ) != null ? resolveVenue( candidate ) : "неизвестно" ).append( "\n" );

        if( candidate.sourceFundingRatePct() != null )
        {
            sb.append( "Ставка финансирования: " ).append( candidate.sourceFundingRatePct() ).append( "%" );
            sb.append( "  ← чем выше, тем сильнее ожидаемое ценовое движение\n" );
        }

        appendTimingSection( sb, candidate, latency );

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
            sb.append( ")  ← нужна для быстрого входа/выхода без проскальзывания\n" );
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

        appendPerformanceSection( sb, perfStats );

        sb.append( "\nКритерии оценки:\n" );
        sb.append( "- GO: ставка > 0.1%, до фандинга < 10 мин, хорошая ликвидность, задержка < 100 ms\n" );
        sb.append( "- WATCH: ставка умеренная ИЛИ до фандинга 10-30 мин ИЛИ ликвидность под вопросом\n" );
        sb.append( "- PASS: ставка < 0.05%, до фандинга > 30 мин, плохая ликвидность или высокая задержка\n" );
        sb.append( "\nОтветь СТРОГО в формате JSON (без markdown):\n" );
        sb.append( "{\"recommendation\":\"GO\",\"confidence\":0.8,\"reasoning\":\"...\"}\n" );
        sb.append( "Где recommendation — одно из: GO, WATCH, PASS\n" );
        sb.append( "confidence — число от 0.0 до 1.0\n" );
        sb.append( "reasoning — 2-3 предложения на русском: объясни ожидаемое ценовое движение, а не сбор фандинга\n" );

        return sb.toString();
    }

    private void appendTimingSection( StringBuilder sb, SignalCandidate candidate, VenueRequestTimingService.Snapshot latency )
    {
        if( candidate.sourceFundingTime() == null )
        {
            return;
        }
        long secondsUntilFunding = Duration.between( Instant.now(), candidate.sourceFundingTime() ).toSeconds();

        if( secondsUntilFunding < 0 )
        {
            sb.append( "До момента фандинга: ПРОСРОЧЕН (фандинг уже прошёл " )
              .append( Math.abs( secondsUntilFunding ) )
              .append( " сек назад)  ← рекомендация PASS\n" );
            return;
        }

        String urgency;
        if( secondsUntilFunding <= 60 )
        {
            urgency = "CRITICAL — немедленный старт";
            sb.append( "До момента фандинга: " ).append( secondsUntilFunding ).append( " сек  ← " ).append( urgency ).append( "\n" );
        }
        else if( secondsUntilFunding <= 300 )
        {
            urgency = "TIGHT";
            sb.append( "До момента фандинга: " ).append( secondsUntilFunding ).append( " сек (~" )
              .append( secondsUntilFunding / 60 ).append( " мин)  ← " ).append( urgency ).append( "\n" );
        }
        else if( secondsUntilFunding <= 600 )
        {
            urgency = "OPTIMAL";
            sb.append( "До момента фандинга: " ).append( secondsUntilFunding / 60 ).append( " мин  ← " ).append( urgency ).append( "\n" );
        }
        else if( secondsUntilFunding <= 1800 )
        {
            urgency = "EARLY — сигнал актуален, но рановато";
            sb.append( "До момента фандинга: " ).append( secondsUntilFunding / 60 ).append( " мин  ← " ).append( urgency ).append( "\n" );
        }
        else
        {
            urgency = "PREMATURE — слишком рано для торговли";
            sb.append( "До момента фандинга: " ).append( secondsUntilFunding / 60 ).append( " мин  ← " ).append( urgency ).append( "\n" );
        }

        // Estimated execution attempts in the warmup window
        if( latency != null && latency.p50DurationMs() != null && latency.p50DurationMs() > 0
            && secondsUntilFunding > 0 && secondsUntilFunding <= 600 )
        {
            long warmupBudgetMs = 500L;
            long usableWindowMs = secondsUntilFunding * 1000L - warmupBudgetMs;
            if( usableWindowMs > 0 )
            {
                long estimatedAttempts = usableWindowMs / latency.p50DurationMs();
                sb.append( "Оценочное окно исполнения: ~" ).append( estimatedAttempts )
                  .append( " попыток (p50=" ).append( latency.p50DurationMs() )
                  .append( "ms, warmup=500ms)\n" );
            }
        }
    }

    private void appendPerformanceSection( StringBuilder sb, AiAdvisorPerformanceService.PerformanceStats perfStats )
    {
        if( perfStats == null || perfStats.totalTrades() < 3 )
        {
            return;
        }
        sb.append( "\nИсторическая эффективность советника (последние " ).append( perfStats.totalTrades() ).append( " сделок):\n" );
        for( AiAdvisorPerformanceService.RecommendationStat stat : perfStats.stats() )
        {
            if( stat.tradeCount() == 0 )
            {
                sb.append( "- " ).append( stat.recommendation() ).append( ": сделки не открывались\n" );
            }
            else
            {
                String winPct = stat.winRate() != null
                    ? String.format( "%.0f%%", stat.winRate() * 100 )
                    : "—";
                String avgPnl = stat.avgPnlUsd() != null
                    ? (stat.avgPnlUsd().compareTo( java.math.BigDecimal.ZERO ) >= 0 ? "+" : "") + "$" + stat.avgPnlUsd()
                    : "—";
                sb.append( "- " ).append( stat.recommendation() ).append( ": " )
                  .append( stat.tradeCount() ).append( " сделок, win rate " ).append( winPct )
                  .append( ", avg PnL " ).append( avgPnl ).append( "\n" );
            }
        }
        sb.append( "(Используй эти данные для калибровки порогов GO/WATCH/PASS)\n" );
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
