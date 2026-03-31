package com.crypto.funding.application.query;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.infrastructure.persistence.mapper.ArmedTradeMapper;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TradeQueryService
{
    private final ArmedTradeJpaRepository armedTradeRepository;

    public TradeQueryService( ArmedTradeJpaRepository armedTradeRepository )
    {
        this.armedTradeRepository = armedTradeRepository;
    }

    @Transactional(readOnly = true)
    public List<ArmedTrade> listArmedTrades()
    {
        return armedTradeRepository.findAllByOrderByCreatedAtDesc()
                                   .stream()
                                   .map( ArmedTradeMapper::toDomain )
                                   .toList();
    }

    @Transactional(readOnly = true)
    public ArmedTrade getArmedTrade( Long id )
    {
        return armedTradeRepository.findById( id )
                                   .map( ArmedTradeMapper::toDomain )
                                   .orElseThrow( () -> new ResourceNotFoundException( "ArmedTrade not found: " + id ) );
    }
}
