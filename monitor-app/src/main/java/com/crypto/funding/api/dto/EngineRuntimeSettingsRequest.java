package com.crypto.funding.api.dto;

import jakarta.validation.constraints.Min;

public record EngineRuntimeSettingsRequest(
    Boolean executionLoopEnabled,
    @Min(100) Long executionLoopIntervalMs
)
{
}
