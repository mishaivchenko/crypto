package com.crypto.funding.persistence.repository;

import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ApprovedFundingRepository
    extends JpaRepository<ApprovedFundingEntity, Long>
{

    List<ApprovedFundingEntity> findByActiveTrueAndExecutedFalse();

    /**
     * Pending fundings whose nextFundingAt is after the given time.
     * Useful for UI lists.
     */
    List<ApprovedFundingEntity> findByActiveTrueAndExecutedFalseAndNextFundingAtAfter(Instant time);

    /**
     * Pending fundings whose nextFundingAt is before the given time.
     * Useful for scheduler "due" window.
     */
    List<ApprovedFundingEntity> findByActiveTrueAndExecutedFalseAndNextFundingAtBefore(Instant time);

    /**
     * Scheduler-friendly: all fundings in the given time window.
     */
    List<ApprovedFundingEntity> findByActiveTrueAndExecutedFalseAndNextFundingAtBetween(Instant from, Instant to);

    Optional<ApprovedFundingEntity> findBySymbol(String symbol);

    Optional<ApprovedFundingEntity> findBySymbolAndActive(String symbol, boolean active);

    Optional<ApprovedFundingEntity> findFirstByActiveTrueAndExecutedFalseOrderByNextFundingAtAsc();
}
