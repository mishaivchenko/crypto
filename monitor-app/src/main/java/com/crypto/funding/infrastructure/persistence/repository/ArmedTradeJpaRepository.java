package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface ArmedTradeJpaRepository extends JpaRepository<ArmedTradeEntity, Long>
{
    List<ArmedTradeEntity> findAllByOrderByCreatedAtDesc();

    List<ArmedTradeEntity> findAllByFundingEventIdOrderByCreatedAtDesc( Long fundingEventId );

    boolean existsByFundingEventIdAndStateIn( Long fundingEventId, Set<ArmedTradeState> states );

    List<ArmedTradeEntity> findAllByStateIn( Set<ArmedTradeState> states );

    /**
     * Returns the count of trades with the given state, grouped by venue.
     * Each element is an Object[2]: [venue (String), count (Long)].
     */
    @Query(
        "SELECT fe.venue, COUNT(at) " +
        "FROM ArmedTradeEntity at " +
        "JOIN FundingEventEntity fe ON fe.id = at.fundingEventId " +
        "WHERE at.state = :state " +
        "GROUP BY fe.venue"
    )
    List<Object[]> countArmedTradesByVenue( @Param("state") ArmedTradeState state );
}
