package com.crypto.funding.api.dto;

import com.crypto.funding.domain.venue.VenueAccessMode;

import java.util.List;

public record DevTestRunOptionsResponse(
    boolean enabled,
    VenueAccessMode currentMode,
    List<DevTestRunVenueOption> venues,
    EngineRuntimeSettingsResponse engineRuntime,
    List<String> safetyIssues
)
{
}
