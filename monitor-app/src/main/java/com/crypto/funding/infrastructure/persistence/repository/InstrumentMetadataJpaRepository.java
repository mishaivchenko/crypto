package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.domain.venue.InstrumentStatus;
import com.crypto.funding.infrastructure.persistence.model.InstrumentMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstrumentMetadataJpaRepository extends JpaRepository<InstrumentMetadataEntity, Long>
{
    Optional<InstrumentMetadataEntity> findByVenueAndCanonicalSymbolAndStatus(
        String venue,
        String canonicalSymbol,
        InstrumentStatus status
    );

    List<InstrumentMetadataEntity> findAllByCanonicalSymbolAndStatusOrderByVenueAsc(
        String canonicalSymbol,
        InstrumentStatus status
    );

    List<InstrumentMetadataEntity> findAllByVenueOrderByCanonicalSymbolAsc( String venue );

    long countByVenueAndStatus( String venue, InstrumentStatus status );
}
