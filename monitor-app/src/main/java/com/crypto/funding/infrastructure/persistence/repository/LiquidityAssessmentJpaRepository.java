package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.LiquidityAssessmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LiquidityAssessmentJpaRepository extends JpaRepository<LiquidityAssessmentEntity, Long>
{
    Optional<LiquidityAssessmentEntity> findFirstByTradeIdOrderBySampledAtDesc( Long tradeId );

    Optional<LiquidityAssessmentEntity> findByAssessmentId( String assessmentId );
}
