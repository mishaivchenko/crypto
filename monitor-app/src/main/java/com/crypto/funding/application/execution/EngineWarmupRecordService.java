package com.crypto.funding.application.execution;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.contract.engine.WarmupCalibrationRequest;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngineWarmupRecordService
{
    private final ArmedTradeJpaRepository armedTradeRepository;

    public EngineWarmupRecordService( ArmedTradeJpaRepository armedTradeRepository )
    {
        this.armedTradeRepository = armedTradeRepository;
    }

    @Transactional
    public void record( Long armedTradeId, WarmupCalibrationRequest request )
    {
        var entity = armedTradeRepository.findById( armedTradeId )
                                         .orElseThrow( () -> new ResourceNotFoundException(
                                             "Подготовленная сделка не найдена: " + armedTradeId
                                         ) );
        entity.setWarmupP50Ms( request.p50Ms() );
        entity.setWarmupP95Ms( request.p95Ms() );
        entity.setWarmupFallbackUsed( request.fallbackUsed() );
        entity.setWarmupDoneAt( request.doneAt() );
        armedTradeRepository.save( entity );
    }
}
