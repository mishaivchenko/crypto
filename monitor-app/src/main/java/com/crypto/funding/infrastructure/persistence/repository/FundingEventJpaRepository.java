package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FundingEventJpaRepository extends JpaRepository<FundingEventEntity, Long>, JpaSpecificationExecutor<FundingEventEntity>
{
    List<FundingEventEntity> findAllByStatusInAndFundingTimeLessThanEqual( Collection<com.crypto.funding.domain.event.FundingEventStatus> statuses, Instant fundingTime );

    Optional<FundingEventEntity> findBySignalCandidateId( Long signalCandidateId );
}
