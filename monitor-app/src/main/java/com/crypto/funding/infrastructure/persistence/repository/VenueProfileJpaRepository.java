package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.VenueProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueProfileJpaRepository extends JpaRepository<VenueProfileEntity, String>
{
}
