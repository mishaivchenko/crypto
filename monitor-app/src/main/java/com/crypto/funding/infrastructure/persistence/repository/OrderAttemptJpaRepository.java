package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.OrderAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderAttemptJpaRepository extends JpaRepository<OrderAttemptEntity, Long>
{
}
