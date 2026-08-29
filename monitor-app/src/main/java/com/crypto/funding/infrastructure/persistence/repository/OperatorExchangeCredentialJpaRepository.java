package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.infrastructure.persistence.model.OperatorExchangeCredentialEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorExchangeCredentialJpaRepository extends JpaRepository<OperatorExchangeCredentialEntity, Long> {
    List<OperatorExchangeCredentialEntity> findAllByOperatorIdOrderByVenueAscModeAsc(Long operatorId);

    Optional<OperatorExchangeCredentialEntity> findByOperatorIdAndVenueAndMode(
            Long operatorId, String venue, VenueAccessMode mode);

    void deleteByOperatorIdAndVenueAndMode(Long operatorId, String venue, VenueAccessMode mode);

    Optional<OperatorExchangeCredentialEntity> findFirstByVenueAndMode(String venue, VenueAccessMode mode);
}
