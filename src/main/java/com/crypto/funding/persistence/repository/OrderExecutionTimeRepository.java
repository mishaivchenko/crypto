package com.crypto.funding.persistence.repository;

import com.crypto.funding.persistence.model.OrderExecutionTimeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderExecutionTimeRepository extends JpaRepository<OrderExecutionTimeEntity, Long>
{
}
