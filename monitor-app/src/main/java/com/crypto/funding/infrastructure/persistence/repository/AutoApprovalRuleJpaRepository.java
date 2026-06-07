package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.AutoApprovalRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutoApprovalRuleJpaRepository extends JpaRepository<AutoApprovalRuleEntity, Long>
{
    List<AutoApprovalRuleEntity> findAllByOrderByPriorityAsc();

    List<AutoApprovalRuleEntity> findAllByEnabledTrueOrderByPriorityAsc();
}
