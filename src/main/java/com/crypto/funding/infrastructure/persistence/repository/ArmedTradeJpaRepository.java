package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArmedTradeJpaRepository extends JpaRepository<ArmedTradeEntity, Long>
{
    List<ArmedTradeEntity> findAllByOrderByCreatedAtDesc();
}
