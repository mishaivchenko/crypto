package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.PositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionJpaRepository extends JpaRepository<PositionEntity, Long>
{
}
