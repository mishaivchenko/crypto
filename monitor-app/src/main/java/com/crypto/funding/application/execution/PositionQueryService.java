package com.crypto.funding.application.execution;

import com.crypto.funding.infrastructure.persistence.model.PositionEntity;
import com.crypto.funding.infrastructure.persistence.repository.PositionJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
public class PositionQueryService
{
    public record PositionSummary(
        String state,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal exitPrice,
        Instant openedAt,
        Instant closedAt
    )
    {
    }

    private final PositionJpaRepository positionRepository;

    public PositionQueryService( PositionJpaRepository positionRepository )
    {
        this.positionRepository = positionRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PositionSummary> findByArmedTrade( Long armedTradeId )
    {
        return positionRepository.findFirstByArmedTradeIdOrderByCreatedAtDesc( armedTradeId )
                                 .map( this::toSummary );
    }

    private PositionSummary toSummary( PositionEntity entity )
    {
        return new PositionSummary(
            entity.getState() == null ? null : entity.getState().name(),
            entity.getQuantity(),
            entity.getEntryPrice(),
            entity.getExitPrice(),
            entity.getOpenedAt(),
            entity.getClosedAt()
        );
    }
}
