package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.VenueTimingProfileEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueTimingProfileJpaRepository extends JpaRepository<VenueTimingProfileEntity, Long> {
    Optional<VenueTimingProfileEntity> findFirstByVenueAndSymbolOrderBySampledAtDesc(String venue, String symbol);
}
