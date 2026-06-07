package com.crypto.funding.application.autoapproval;

import com.crypto.funding.application.ai.AiSignalAdvisorService;
import com.crypto.funding.application.candidate.RejectSignalCandidateCommand;
import com.crypto.funding.application.candidate.SignalCandidateQueryService;
import com.crypto.funding.application.candidate.SignalCandidateReviewService;
import com.crypto.funding.application.liquidity.LiquidityAssessmentService;
import com.crypto.funding.config.AutoApprovalProperties;
import com.crypto.funding.config.MonitorRiskProperties;
import com.crypto.funding.domain.autoapproval.AutoApprovalAction;
import com.crypto.funding.domain.autoapproval.AutoApprovalRule;
import com.crypto.funding.domain.candidate.ReviewDecision;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoApprovalPipelineServiceTest
{
    private static final Instant NOW = Instant.parse( "2030-01-01T12:00:00Z" );
    private static final Instant FUNDING_SOON = NOW.plusSeconds( 300 );

    private AutoApprovalProperties properties;
    private AutoApprovalRuleService ruleService;
    private SignalCandidateQueryService candidateQueryService;
    private SignalCandidateReviewService candidateReviewService;
    private AutoApprovalExecutor executor;
    private AiSignalAdvisorService aiSignalAdvisorService;
    private LiquidityAssessmentService liquidityAssessmentService;
    private MonitorRiskProperties riskProperties;
    private AutoApprovalPipelineService service;

    @BeforeEach
    void setUp()
    {
        properties = new AutoApprovalProperties();
        properties.setEnabled( true );
        properties.setMaxNotionalUsd( new BigDecimal( "500" ) );

        ruleService = mock( AutoApprovalRuleService.class );
        candidateQueryService = mock( SignalCandidateQueryService.class );
        candidateReviewService = mock( SignalCandidateReviewService.class );
        executor = mock( AutoApprovalExecutor.class );
        aiSignalAdvisorService = mock( AiSignalAdvisorService.class );
        liquidityAssessmentService = mock( LiquidityAssessmentService.class );
        riskProperties = new MonitorRiskProperties();

        com.crypto.funding.application.venue.VenueProfileService venueProfileService =
            mock( com.crypto.funding.application.venue.VenueProfileService.class );
        com.crypto.funding.application.venue.VenueProfileService.GlobalAccessProfile globalProfile =
            new com.crypto.funding.application.venue.VenueProfileService.GlobalAccessProfile(
                com.crypto.funding.domain.venue.VenueAccessMode.TESTNET, false,
                List.of( com.crypto.funding.domain.venue.VenueAccessMode.TESTNET,
                         com.crypto.funding.domain.venue.VenueAccessMode.PRODUCTION )
            );
        when( venueProfileService.getGlobalAccessProfile() ).thenReturn( globalProfile );

        service = new AutoApprovalPipelineService(
            properties, ruleService, candidateQueryService, candidateReviewService,
            executor, aiSignalAdvisorService, liquidityAssessmentService,
            riskProperties, venueProfileService
        );

        when( aiSignalAdvisorService.findLatest( any() ) ).thenReturn( Optional.empty() );
        when( liquidityAssessmentService.findLatestForCandidate( any() ) ).thenReturn( Optional.empty() );
    }

    @Test
    void doesNothingWhenGloballyDisabled()
    {
        properties.setEnabled( false );
        service.tryAutoProcess( 1L );
        verify( candidateQueryService, never() ).getCandidate( any() );
    }

    @Test
    void doesNothingWhenCandidateNotNormalized()
    {
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate( SignalCandidateStatus.NEW ) );
        when( ruleService.listActive() ).thenReturn( List.of() );
        service.tryAutoProcess( 1L );
        verify( executor, never() ).approveAndArm( any(), any(), any(), any(), any(), any(), any() );
        verify( candidateReviewService, never() ).reject( any() );
    }

    @Test
    void doesNothingWhenNoMatchingRule()
    {
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate( SignalCandidateStatus.NORMALIZED ) );
        when( ruleService.listActive() ).thenReturn( List.of() );
        service.tryAutoProcess( 1L );
        verify( executor, never() ).approveAndArm( any(), any(), any(), any(), any(), any(), any() );
        verify( candidateReviewService, never() ).reject( any() );
    }

    @Test
    void doesNothingWhenVenueIsDisabled()
    {
        riskProperties.setDisabledVenues( "gate" );
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate( SignalCandidateStatus.NORMALIZED ) );
        when( ruleService.listActive() ).thenReturn( List.of( autoExecuteRule( new BigDecimal( "100" ) ) ) );
        service.tryAutoProcess( 1L );
        verify( executor, never() ).approveAndArm( any(), any(), any(), any(), any(), any(), any() );
    }

    @Test
    void autoExecuteCallsExecutorWhenRuleMatches()
    {
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate( SignalCandidateStatus.NORMALIZED ) );
        when( ruleService.listActive() ).thenReturn( List.of( autoExecuteRule( new BigDecimal( "100" ) ) ) );
        when( executor.approveAndArm( any(), any(), any(), any(), any(), any(), any() ) ).thenReturn( armedTrade() );

        service.tryAutoProcess( 1L );

        verify( executor ).approveAndArm( eq( 1L ), any(), any(), any(), any(), any(), any() );
    }

    @Test
    void autoRejectRejectsWhenRuleActionIsReject()
    {
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate( SignalCandidateStatus.NORMALIZED ) );
        when( ruleService.listActive() ).thenReturn( List.of( autoRejectRule() ) );

        SignalCandidate rejected = candidate( SignalCandidateStatus.REJECTED );
        when( candidateReviewService.reject( any() ) ).thenReturn( rejected );

        service.tryAutoProcess( 1L );

        verify( candidateReviewService ).reject( any( RejectSignalCandidateCommand.class ) );
        verify( executor, never() ).approveAndArm( any(), any(), any(), any(), any(), any(), any() );
    }

    @Test
    void riskGuardBlocksWhenNotionalExceedsCap()
    {
        properties.setMaxNotionalUsd( new BigDecimal( "100" ) );
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate( SignalCandidateStatus.NORMALIZED ) );
        when( ruleService.listActive() ).thenReturn( List.of( autoExecuteRule( new BigDecimal( "500" ) ) ) );

        service.tryAutoProcess( 1L );

        verify( executor, never() ).approveAndArm( any(), any(), any(), any(), any(), any(), any() );
    }

    @Test
    void executorFailureIsSwallowedByTryAutoProcess()
    {
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate( SignalCandidateStatus.NORMALIZED ) );
        when( ruleService.listActive() ).thenReturn( List.of( autoExecuteRule( new BigDecimal( "100" ) ) ) );
        when( executor.approveAndArm( any(), any(), any(), any(), any(), any(), any() ) )
            .thenThrow( new RuntimeException( "arm failed — rolls back approval too" ) );

        // must not propagate
        service.tryAutoProcess( 1L );

        verify( executor ).approveAndArm( any(), any(), any(), any(), any(), any(), any() );
    }

    @Test
    void executorReceivesActorRefContainingRuleId()
    {
        AutoApprovalRule rule = autoExecuteRule( new BigDecimal( "100" ) );
        when( candidateQueryService.getCandidate( 1L ) ).thenReturn( candidate( SignalCandidateStatus.NORMALIZED ) );
        when( ruleService.listActive() ).thenReturn( List.of( rule ) );
        when( executor.approveAndArm( any(), any(), any(), any(), any(), any(), any() ) ).thenReturn( armedTrade() );

        service.tryAutoProcess( 1L );

        verify( executor ).approveAndArm(
            eq( 1L ), any(), any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.argThat( ref -> ref != null && ref.contains( "rule-" + rule.id() ) )
        );
    }

    // --- helpers ---

    private static SignalCandidate candidate( SignalCandidateStatus status )
    {
        return new SignalCandidate(
            1L, "FUNDING_API", null, null, null,
            "gate", "BTC/USDT", "BTC/USDT",
            List.of( "gate" ),
            NOW, status,
            null, null, null, null,
            FUNDING_SOON, new BigDecimal( "0.10" ), null, NOW, NOW
        );
    }

    private static AutoApprovalRule autoExecuteRule( BigDecimal notional )
    {
        return new AutoApprovalRule(
            1L, "test-rule", true, "BOTH",
            null, null, List.of(), List.of(), null, List.of(),
            notional, TradeSide.SHORT,
            AutoApprovalAction.AUTO_EXECUTE, 1, null, NOW, NOW
        );
    }

    private static AutoApprovalRule autoRejectRule()
    {
        return new AutoApprovalRule(
            2L, "reject-rule", true, "BOTH",
            null, null, List.of(), List.of(), null, List.of(),
            new BigDecimal( "100" ), TradeSide.SHORT,
            AutoApprovalAction.AUTO_REJECT, 1, null, NOW, NOW
        );
    }

    private static ArmedTrade armedTrade()
    {
        return mock( ArmedTrade.class );
    }
}
