package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.SignalCandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SignalCandidateJpaRepository extends JpaRepository<SignalCandidateEntity, Long>, JpaSpecificationExecutor<SignalCandidateEntity>
{
    Optional<SignalCandidateEntity> findBySourceTypeAndSourceChatIdAndSourceMessageId(
        String sourceType,
        Long sourceChatId,
        Long sourceMessageId
    );

    Optional<SignalCandidateEntity> findFirstBySourceTypeAndRawSymbolAndDetectedAtGreaterThanEqualOrderByDetectedAtDesc(
        String sourceType,
        String rawSymbol,
        Instant detectedAt
    );

    List<SignalCandidateEntity> findAllBySourceTypeAndSourceChatIdAndFundingEventIdIsNullOrderByDetectedAtDesc(
        String sourceType,
        Long sourceChatId
    );

    List<SignalCandidateEntity> findAllByOrderByDetectedAtDesc();
}
