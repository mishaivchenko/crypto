package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FundingEventJpaRepository extends JpaRepository<FundingEventEntity, Long>, JpaSpecificationExecutor<FundingEventEntity>
{
}
