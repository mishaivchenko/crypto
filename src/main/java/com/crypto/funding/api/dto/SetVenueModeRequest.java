package com.crypto.funding.api.dto;

import com.crypto.funding.domain.venue.VenueAccessMode;
import jakarta.validation.constraints.NotNull;

public record SetVenueModeRequest(
    @NotNull VenueAccessMode mode
)
{
}
