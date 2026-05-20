package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.AiSignalAdviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiSignalAdviceJpaRepository extends JpaRepository<AiSignalAdviceEntity, Long>
{
    Optional<AiSignalAdviceEntity> findFirstBySignalCandidateIdOrderByAnalyzedAtDesc( Long signalCandidateId );
}
