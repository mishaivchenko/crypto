package com.crypto.funding.application.autoapproval;

import com.crypto.funding.application.ai.AiSignalAdvisorService;
import com.crypto.funding.application.candidate.ApproveSignalCandidateCommand;
import com.crypto.funding.application.candidate.RejectSignalCandidateCommand;
import com.crypto.funding.application.candidate.SignalCandidateQueryService;
import com.crypto.funding.application.candidate.SignalCandidateReviewService;
import com.crypto.funding.application.event.ArmFundingEventCommand;
import com.crypto.funding.application.event.FundingEventArmService;
import com.crypto.funding.application.liquidity.LiquidityAssessmentService;
import com.crypto.funding.config.AutoApprovalProperties;
import com.crypto.funding.config.MonitorRiskProperties;
import com.crypto.funding.domain.ai.AiSignalAdvice;
import com.crypto.funding.domain.autoapproval.AutoApprovalAction;
import com.crypto.funding.domain.autoapproval.AutoApprovalEvaluator;
import com.crypto.funding.domain.autoapproval.AutoApprovalRule;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.domain.liquidity.LiquidityAssessment;
import com.crypto.funding.domain.trade.ArmedTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AutoApprovalPipelineService
{
    private static final Logger log = LoggerFactory.getLogger( AutoApprovalPipelineService.class );

    private final AutoApprovalProperties properties;
    private final AutoApprovalRuleService ruleService;
    private final SignalCandidateQueryService candidateQueryService;
    private final SignalCandidateReviewService candidateReviewService;
    private final FundingEventArmService fundingEventArmService;
    private final AiSignalAdvisorService aiSignalAdvisorService;
    private final LiquidityAssessmentService liquidityAssessmentService;
    private final MonitorRiskProperties riskProperties;

    public AutoApprovalPipelineService(
        AutoApprovalProperties properties,
        AutoApprovalRuleService ruleService,
        SignalCandidateQueryService candidateQueryService,
        SignalCandidateReviewService candidateReviewService,
        FundingEventArmService fundingEventArmService,
        AiSignalAdvisorService aiSignalAdvisorService,
        LiquidityAssessmentService liquidityAssessmentService,
        MonitorRiskProperties riskProperties
    )
    {
        this.properties = properties;
        this.ruleService = ruleService;
        this.candidateQueryService = candidateQueryService;
        this.candidateReviewService = candidateReviewService;
        this.fundingEventArmService = fundingEventArmService;
        this.aiSignalAdvisorService = aiSignalAdvisorService;
        this.liquidityAssessmentService = liquidityAssessmentService;
        this.riskProperties = riskProperties;
    }

    @Async
    public void tryAutoProcess( Long candidateId )
    {
        if( !properties.isEnabled() )
        {
            return;
        }

        try
        {
            doProcess( candidateId );
        }
        catch( Exception e )
        {
            log.warn( "Auto-approval pipeline failed for candidate {}: {}", candidateId, e.getMessage() );
        }
    }

    private void doProcess( Long candidateId )
    {
        SignalCandidate candidate = candidateQueryService.getCandidate( candidateId );
        if( candidate.status() != SignalCandidateStatus.NORMALIZED )
        {
            return;
        }

        String venue = resolveVenue( candidate );
        if( venue != null && riskProperties.getDisabledVenues().contains( venue ) )
        {
            log.debug( "Auto-approval skipped for candidate {} — venue {} is disabled", candidateId, venue );
            return;
        }

        AiSignalAdvice advice = aiSignalAdvisorService.findLatest( candidateId ).orElse( null );
        LiquidityAssessment liquidity = liquidityAssessmentService.findLatestForCandidate( candidateId ).orElse( null );
        List<AutoApprovalRule> activeRules = ruleService.listActive();

        Optional<AutoApprovalEvaluator.RuleMatch> match = AutoApprovalEvaluator.evaluate( candidate, advice, liquidity, activeRules );
        if( match.isEmpty() )
        {
            log.debug( "Auto-approval: no matching rule for candidate {} — left for manual review", candidateId );
            return;
        }

        AutoApprovalEvaluator.RuleMatch ruleMatch = match.get();
        if( ruleMatch.action() == AutoApprovalAction.AUTO_EXECUTE )
        {
            autoExecute( candidate, ruleMatch.rule() );
        }
        else
        {
            autoReject( candidate, ruleMatch.rule() );
        }
    }

    private void autoExecute( SignalCandidate candidate, AutoApprovalRule rule )
    {
        log.info( "Auto-approval AUTO_EXECUTE candidate {} via rule '{}' (id={})", candidate.id(), rule.name(), rule.id() );

        SignalCandidate approved = candidateReviewService.approve( new ApproveSignalCandidateCommand(
            candidate.id(),
            resolveVenue( candidate ),
            candidate.normalizedSymbol(),
            candidate.sourceFundingTime(),
            candidate.sourceFundingRatePct(),
            "auto-approval:rule-" + rule.id()
        ) );

        if( approved.fundingEventId() == null )
        {
            log.warn( "Auto-approval: approve did not produce fundingEventId for candidate {}", candidate.id() );
            return;
        }

        ArmedTrade armedTrade = fundingEventArmService.arm(
            approved.fundingEventId(),
            new ArmFundingEventCommand(
                rule.defaultNotionalUsd(),
                rule.defaultSide(),
                null,
                null,
                null,
                null,
                null,
                "auto-approval:rule-" + rule.id()
            )
        );
        log.info( "Auto-approval: armed trade {} created for candidate {} via rule '{}'", armedTrade.id(), candidate.id(), rule.name() );
    }

    private void autoReject( SignalCandidate candidate, AutoApprovalRule rule )
    {
        log.info( "Auto-approval AUTO_REJECT candidate {} via rule '{}' (id={})", candidate.id(), rule.name(), rule.id() );
        candidateReviewService.reject( new RejectSignalCandidateCommand(
            candidate.id(),
            "auto-approval:rule-" + rule.id()
        ) );
    }

    private static String resolveVenue( SignalCandidate candidate )
    {
        if( candidate.sourceVenue() != null && !candidate.sourceVenue().isBlank() )
        {
            return candidate.sourceVenue();
        }
        List<String> hints = candidate.venueHints();
        if( hints != null && hints.size() == 1 )
        {
            return hints.get( 0 );
        }
        return null;
    }
}
