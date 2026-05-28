package com.crypto.funding.application.ai;

import com.crypto.funding.application.candidate.SignalCandidateQueryService;
import com.crypto.funding.application.liquidity.LiquidityAssessmentService;
import com.crypto.funding.config.DeepSeekProperties;
import com.crypto.funding.domain.ai.AiRecommendation;
import com.crypto.funding.domain.ai.AiSignalAdvice;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.domain.liquidity.LiquidityAssessment;
import com.crypto.funding.domain.liquidity.LiquidityScore;
import com.crypto.funding.domain.trade.TradeSide;
import com.crypto.funding.infrastructure.ai.DeepSeekClient;
import com.crypto.funding.infrastructure.persistence.model.AiSignalAdviceEntity;
import com.crypto.funding.infrastructure.persistence.repository.AiSignalAdviceJpaRepository;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiSignalAdvisorServiceTest
{
    private DeepSeekProperties deepSeekProperties;
    private DeepSeekClient deepSeekClient;
    private SignalCandidateQueryService candidateQueryService;
    private LiquidityAssessmentService liquidityAssessmentService;
    private VenueRequestTimingService venueRequestTimingService;
    private AiSignalAdviceJpaRepository adviceRepository;
    private AiAdvisorPerformanceService performanceService;
    private AiSignalAdvisorService service;

    @BeforeEach
    void setUp()
    {
        deepSeekProperties = mock( DeepSeekProperties.class );
        deepSeekClient = mock( DeepSeekClient.class );
        candidateQueryService = mock( SignalCandidateQueryService.class );
        liquidityAssessmentService = mock( LiquidityAssessmentService.class );
        venueRequestTimingService = mock( VenueRequestTimingService.class );
        adviceRepository = mock( AiSignalAdviceJpaRepository.class );
        performanceService = mock( AiAdvisorPerformanceService.class );
        when( performanceService.getPerformanceStats() )
            .thenReturn( new AiAdvisorPerformanceService.PerformanceStats( List.of(), 0 ) );

        service = new AiSignalAdvisorService(
            deepSeekProperties,
            deepSeekClient,
            candidateQueryService,
            liquidityAssessmentService,
            venueRequestTimingService,
            adviceRepository,
            performanceService
        );
    }

    @Test
    void analyzeAsync_whenDisabled_doesNotCallDeepSeek()
    {
        when( deepSeekProperties.isEnabled() ).thenReturn( false );

        service.analyzeAsync( 1L );

        verifyNoInteractions( deepSeekClient );
    }

    @Test
    void analyzeAsync_whenEnabled_callsDeepSeek()
    {
        when( deepSeekProperties.isEnabled() ).thenReturn( true );
        SignalCandidate candidate = candidateWith( "BTC/USDT", "bybit", null );
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate );
        when( liquidityAssessmentService.findLatestForCandidate( any() ) ).thenReturn( Optional.empty() );
        when( venueRequestTimingService.snapshot( eq( "bybit" ), any() ) ).thenReturn( null );
        when( deepSeekClient.analyze( any() ) ).thenReturn( adviceResult( AiRecommendation.GO ) );
        when( adviceRepository.save( any() ) ).thenAnswer( inv -> savedEntity( (AiSignalAdviceEntity) inv.getArgument( 0 ) ) );

        service.analyzeAsync( 1L );

        verify( deepSeekClient ).analyze( any() );
    }

    @Test
    void analyzeAsync_whenDeepSeekThrows_swallowsException()
    {
        when( deepSeekProperties.isEnabled() ).thenReturn( true );
        SignalCandidate candidate = candidateWith( "BTC/USDT", "bybit", null );
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate );
        when( liquidityAssessmentService.findLatestForCandidate( any() ) ).thenReturn( Optional.empty() );
        when( deepSeekClient.analyze( any() ) ).thenThrow( new RuntimeException( "timeout" ) );

        assertThatNoException().isThrownBy( () -> service.analyzeAsync( 1L ) );
    }

    @Test
    void analyze_buildsPromptWithFundingRate()
    {
        SignalCandidate candidate = candidateWith( "ETH/USDT", "gate", new BigDecimal( "0.35" ) );
        when( candidateQueryService.getCandidate( 42L ) ).thenReturn( candidate );
        when( liquidityAssessmentService.findLatestForCandidate( any() ) ).thenReturn( Optional.empty() );
        when( deepSeekClient.analyze( any() ) ).thenReturn( adviceResult( AiRecommendation.WATCH ) );
        when( adviceRepository.save( any() ) ).thenAnswer( inv -> savedEntity( (AiSignalAdviceEntity) inv.getArgument( 0 ) ) );

        service.analyze( 42L );

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass( String.class );
        verify( deepSeekClient ).analyze( promptCaptor.capture() );
        assertThat( promptCaptor.getValue() ).contains( "0.35%" );
    }

    @Test
    void analyze_buildsPromptWithLiquidity_whenAvailable()
    {
        SignalCandidate candidate = candidateWith( "BTC/USDT", "bybit", null );
        LiquidityAssessment liquidity = liquidityAssessment( "bybit", "BTC/USDT", LiquidityScore.GOOD );
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate );
        when( liquidityAssessmentService.findLatestForCandidate( any() ) ).thenReturn( Optional.of( liquidity ) );
        when( deepSeekClient.analyze( any() ) ).thenReturn( adviceResult( AiRecommendation.GO ) );
        when( adviceRepository.save( any() ) ).thenAnswer( inv -> savedEntity( (AiSignalAdviceEntity) inv.getArgument( 0 ) ) );

        service.analyze( 1L );

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass( String.class );
        verify( deepSeekClient ).analyze( promptCaptor.capture() );
        assertThat( promptCaptor.getValue() ).contains( "GOOD" );
    }

    @Test
    void analyze_buildsPromptWithoutLiquidity_whenUnavailable()
    {
        SignalCandidate candidate = candidateWith( "BTC/USDT", "bybit", null );
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate );
        when( liquidityAssessmentService.findLatestForCandidate( any() ) ).thenReturn( Optional.empty() );
        when( deepSeekClient.analyze( any() ) ).thenReturn( adviceResult( AiRecommendation.PASS ) );
        when( adviceRepository.save( any() ) ).thenAnswer( inv -> savedEntity( (AiSignalAdviceEntity) inv.getArgument( 0 ) ) );

        service.analyze( 1L );

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass( String.class );
        verify( deepSeekClient ).analyze( promptCaptor.capture() );
        assertThat( promptCaptor.getValue() ).contains( "данные недоступны" );
    }

    @Test
    void analyze_savesEntityAndReturnsDomain()
    {
        SignalCandidate candidate = candidateWith( "SOL/USDT", "okx", null );
        when( candidateQueryService.getCandidate( 7L ) ).thenReturn( candidate );
        when( liquidityAssessmentService.findLatestForCandidate( any() ) ).thenReturn( Optional.empty() );
        when( deepSeekClient.analyze( any() ) ).thenReturn( adviceResult( AiRecommendation.GO ) );
        AiSignalAdviceEntity entity = savedEntity( buildEntity( 7L, AiRecommendation.GO, 0.9 ) );
        when( adviceRepository.save( any() ) ).thenReturn( entity );

        AiSignalAdvice result = service.analyze( 7L );

        assertThat( result.recommendation() ).isEqualTo( AiRecommendation.GO );
        assertThat( result.signalCandidateId() ).isEqualTo( 7L );
        assertThat( result.confidence() ).isEqualTo( 0.9 );
    }

    @Test
    void analyze_resolvesVenueFromSourceVenue()
    {
        SignalCandidate candidate = candidateWith( "BTC/USDT", "bybit", null );
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate );
        when( liquidityAssessmentService.findLatestForCandidate( any() ) ).thenReturn( Optional.empty() );
        when( deepSeekClient.analyze( any() ) ).thenReturn( adviceResult( AiRecommendation.GO ) );
        when( adviceRepository.save( any() ) ).thenAnswer( inv -> savedEntity( (AiSignalAdviceEntity) inv.getArgument( 0 ) ) );

        service.analyze( 1L );

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass( String.class );
        verify( deepSeekClient ).analyze( promptCaptor.capture() );
        assertThat( promptCaptor.getValue() ).contains( "bybit" );
    }

    @Test
    void analyze_resolvesVenueFromVenueHints_whenSourceVenueBlank()
    {
        SignalCandidate candidate = new SignalCandidate(
            1L, "FUNDING_API", 0L, 0L, "{}", null, "BTC/USDT", "BTC/USDT",
            List.of( "gate" ), Instant.now(), SignalCandidateStatus.NORMALIZED,
            null, null, null, null, null, null, null, Instant.now(), Instant.now()
        );
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate );
        when( liquidityAssessmentService.findLatestForCandidate( any() ) ).thenReturn( Optional.empty() );
        when( deepSeekClient.analyze( any() ) ).thenReturn( adviceResult( AiRecommendation.WATCH ) );
        when( adviceRepository.save( any() ) ).thenAnswer( inv -> savedEntity( (AiSignalAdviceEntity) inv.getArgument( 0 ) ) );

        service.analyze( 1L );

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass( String.class );
        verify( deepSeekClient ).analyze( promptCaptor.capture() );
        assertThat( promptCaptor.getValue() ).contains( "gate" );
    }

    @Test
    void findLatest_delegatesToRepository()
    {
        when( adviceRepository.findFirstBySignalCandidateIdOrderByAnalyzedAtDesc( 5L ) )
            .thenReturn( Optional.empty() );

        Optional<AiSignalAdvice> result = service.findLatest( 5L );

        assertThat( result ).isEmpty();
        verify( adviceRepository ).findFirstBySignalCandidateIdOrderByAnalyzedAtDesc( 5L );
    }

    @Test
    void analyze_promptDescribesPriceMovementStrategy()
    {
        SignalCandidate candidate = candidateWith( "BTC/USDT", "bybit", new BigDecimal( "0.15" ) );
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate );
        when( liquidityAssessmentService.findLatestForCandidate( any() ) ).thenReturn( Optional.empty() );
        when( deepSeekClient.analyze( any() ) ).thenReturn( adviceResult( AiRecommendation.GO ) );
        when( adviceRepository.save( any() ) ).thenAnswer( inv -> savedEntity( (AiSignalAdviceEntity) inv.getArgument( 0 ) ) );

        service.analyze( 1L );

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass( String.class );
        verify( deepSeekClient ).analyze( promptCaptor.capture() );
        String prompt = promptCaptor.getValue();
        assertThat( prompt ).contains( "SHORT" );
        assertThat( prompt ).contains( "ценовое движение" );
        assertThat( prompt ).contains( "ДО момента" );
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private SignalCandidate candidateWith( String symbol, String venue, BigDecimal fundingRate )
    {
        return new SignalCandidate(
            1L, "FUNDING_API", 0L, 0L, "{}", venue, symbol, symbol,
            List.of( venue ), Instant.now(), SignalCandidateStatus.NORMALIZED,
            null, null, null, null, null, fundingRate, null, Instant.now(), Instant.now()
        );
    }

    private LiquidityAssessment liquidityAssessment( String venue, String symbol, LiquidityScore score )
    {
        return new LiquidityAssessment(
            "liq-1", null, 1L, venue, symbol, TradeSide.SHORT,
            new BigDecimal( "29990" ), new BigDecimal( "30000" ),
            new BigDecimal( "3.3" ), new BigDecimal( "10" ),
            new BigDecimal( "50000" ), new BigDecimal( "50000" ),
            new BigDecimal( "40000" ), new BigDecimal( "0.85" ),
            new BigDecimal( "34000" ), score,
            Instant.now(), Instant.now().plusSeconds( 60 )
        );
    }

    private DeepSeekClient.AdviceResult adviceResult( AiRecommendation recommendation )
    {
        return new DeepSeekClient.AdviceResult( recommendation, 0.8, "Test reasoning", "deepseek-chat", 100, 50 );
    }

    private AiSignalAdviceEntity buildEntity( Long candidateId, AiRecommendation recommendation, double confidence )
    {
        AiSignalAdviceEntity entity = new AiSignalAdviceEntity();
        entity.setSignalCandidateId( candidateId );
        entity.setRecommendation( recommendation );
        entity.setConfidence( confidence );
        entity.setReasoning( "Test reasoning" );
        entity.setModelUsed( "deepseek-chat" );
        entity.setPromptTokens( 100 );
        entity.setCompletionTokens( 50 );
        entity.setAnalyzedAt( Instant.now() );
        return entity;
    }

    private AiSignalAdviceEntity savedEntity( AiSignalAdviceEntity entity )
    {
        ReflectionTestUtils.setField( entity, "createdAt", Instant.now() );
        return entity;
    }
}
