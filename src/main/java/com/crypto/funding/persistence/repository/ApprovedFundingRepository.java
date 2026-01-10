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
     * For scheduler: jobs that are due at or before provided time.
     */
    List<ApprovedFundingEntity> findByActiveTrueAndExecutedFalseAndNextFundingAtBefore(Instant time);

    Optional<ApprovedFundingEntity> findBySymbol(String symbol);

    Optional<ApprovedFundingEntity> findBySymbolAndActive( String symbol, boolean active );

    List<ApprovedFundingEntity> findByActiveTrue();
}
