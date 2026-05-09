package com.crypto.funding.api.dto;

import com.crypto.funding.contract.engine.EngineExecutionTargetPhase;
import com.crypto.funding.domain.venue.VenueAccessMode;

public record DevTestRunExecutionResponse(
    Long armedTradeId,
    EngineExecutionTargetPhase phase,
    VenueAccessMode mode,
    EngineRunOnceResponse execution
)
{
}
