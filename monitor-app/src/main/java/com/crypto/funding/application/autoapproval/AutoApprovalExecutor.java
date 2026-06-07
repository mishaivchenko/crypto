package com.crypto.funding.application.autoapproval;

import com.crypto.funding.application.candidate.ApproveSignalCandidateCommand;
import com.crypto.funding.application.candidate.SignalCandidateReviewService;
import com.crypto.funding.application.event.ArmFundingEventCommand;
import com.crypto.funding.application.event.FundingEventArmService;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.TradeArmSource;
import com.crypto.funding.domain.trade.TradeJournalActorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Executes approve + arm as a single transaction so that a failed arm rolls back the approval.
 */
@Service
public class AutoApprovalExecutor
{
    private final SignalCandidateReviewService candidateReviewService;
    private final FundingEventArmService fundingEventArmService;

    public AutoApprovalExecutor(
        SignalCandidateReviewService candidateReviewService,
        FundingEventArmService fundingEventArmService
    )
    {
        this.candidateReviewService = candidateReviewService;
        this.fundingEventArmService = fundingEventArmService;
    }

    @Transactional
    public ArmedTrade approveAndArm(
        Long candidateId,
        String venue,
        String symbol,
        Instant fundingTime,
        BigDecimal fundingRatePct,
        BigDecimal notionalUsd,
        String actorRef
    )
    {
        SignalCandidate approved = candidateReviewService.approve( new ApproveSignalCandidateCommand(
            candidateId,
            venue,
            symbol,
            fundingTime,
            fundingRatePct,
            actorRef
        ) );

        if( approved.fundingEventId() == null )
        {
            throw new IllegalStateException( "Approve did not produce fundingEventId for candidate " + candidateId );
        }

        return fundingEventArmService.arm(
            approved.fundingEventId(),
            new ArmFundingEventCommand(
                notionalUsd,
                null,  // side is SHORT by domain invariant
                fundingTime,  // default entry = funding time; engine computes lead from effectiveLatency
                null,
                null,
                null,
                null,
                actorRef
            ),
            TradeArmSource.AUTO_APPROVAL,
            TradeJournalActorType.SYSTEM,
            actorRef
        );
    }
}
