package com.crypto.funding.application.autoapproval;

import com.crypto.funding.application.ai.AiSignalAdvisorService;
import com.crypto.funding.application.candidate.RejectSignalCandidateCommand;
import com.crypto.funding.application.candidate.SignalCandidateQueryService;
import com.crypto.funding.application.candidate.SignalCandidateReviewService;
import com.crypto.funding.application.liquidity.LiquidityAssessmentService;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.config.AutoApprovalProperties;
import com.crypto.funding.config.MonitorRiskProperties;
import com.crypto.funding.domain.ai.AiSignalAdvice;
import com.crypto.funding.domain.autoapproval.AutoApprovalAction;
import com.crypto.funding.domain.autoapproval.AutoApprovalEvaluator;
import com.crypto.funding.domain.autoapproval.AutoApprovalRule;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.domain.liquidity.LiquidityAssessment;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.venue.VenueAccessMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final AutoApprovalExecutor executor;
    private final AiSignalAdvisorService aiSignalAdvisorService;
    private final LiquidityAssessmentService liquidityAssessmentService;
    private final MonitorRiskProperties riskProperties;
    private final VenueProfileService venueProfileService;

    public AutoApprovalPipelineService(
        AutoApprovalProperties properties,
        AutoApprovalRuleService ruleService,
        SignalCandidateQueryService candidateQueryService,
        SignalCandidateReviewService candidateReviewService,
        AutoApprovalExecutor executor,
        AiSignalAdvisorService aiSignalAdvisorService,
        LiquidityAssessmentService liquidityAssessmentService,
        MonitorRiskProperties riskProperties,
        VenueProfileService venueProfileService
    )
    {
        this.properties = properties;
        this.ruleService = ruleService;
        this.candidateQueryService = candidateQueryService;
        this.candidateReviewService = candidateReviewService;
        this.executor = executor;
        this.aiSignalAdvisorService = aiSignalAdvisorService;
        this.liquidityAssessmentService = liquidityAssessmentService;
        this.riskProperties = riskProperties;
        this.venueProfileService = venueProfileService;
    }

    @Async
    @EventListener
    public void onCandidateReady( CandidateReadyForAutoApprovalEvent event )
    {
        tryAutoProcess( event.candidateId() );
    }

    @Async
    public void sweepNormalized()
    {
        if( !properties.isEnabled() )
        {
            return;
        }
        List<Long> ids = candidateQueryService.findAllIdsByStatus( SignalCandidateStatus.NORMALIZED );
        log.info( "Auto-approval sweep: processing {} NORMALIZED candidates", ids.size() );
        for( Long id : ids )
        {
            tryAutoProcess( id );
        }
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
        if( venue != null && riskProperties.disabledVenues().contains( venue ) )
        {
            log.debug( "Auto-approval skipped for candidate {} — venue {} is disabled", candidateId, venue );
            return;
        }

        VenueAccessMode currentMode = venueProfileService.getGlobalAccessProfile().mode();

        AiSignalAdvice advice = aiSignalAdvisorService.findLatest( candidateId ).orElse( null );
        LiquidityAssessment liquidity = liquidityAssessmentService.findLatestForCandidate( candidateId ).orElse( null );

        List<AutoApprovalRule> activeRules = ruleService.listActive().stream()
            .filter( r -> ruleAppliesToMode( r, currentMode ) )
            .toList();

        Optional<AutoApprovalEvaluator.RuleMatch> match = AutoApprovalEvaluator.evaluate( candidate, advice, liquidity, activeRules );
        if( match.isEmpty() )
        {
            log.debug( "Auto-approval: no matching rule for candidate {} in mode {} — left for manual review", candidateId, currentMode );
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
        BigDecimal notional = rule.defaultNotionalUsd();
        BigDecimal cap = properties.getMaxNotionalUsd();
        if( notional.compareTo( cap ) > 0 )
        {
            log.warn( "Auto-approval: rule '{}' notional {} exceeds global cap {} — aborting for candidate {}",
                rule.name(), notional, cap, candidate.id() );
            return;
        }

        log.info( "Auto-approval AUTO_EXECUTE candidate {} via rule '{}' (id={})", candidate.id(), rule.name(), rule.id() );

        String actorRef = "auto-approval:rule-" + rule.id();

        // approve + arm execute in one transaction — arm failure rolls back the approval
        ArmedTrade armedTrade = executor.approveAndArm(
            candidate.id(),
            resolveVenue( candidate ),
            candidate.normalizedSymbol(),
            candidate.sourceFundingTime(),
            candidate.sourceFundingRatePct(),
            notional,
            actorRef
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

    private static boolean ruleAppliesToMode( AutoApprovalRule rule, VenueAccessMode currentMode )
    {
        String ruleMode = rule.mode();
        if( ruleMode == null || "BOTH".equalsIgnoreCase( ruleMode ) )
        {
            return true;
        }
        return ruleMode.equalsIgnoreCase( currentMode.name() );
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
