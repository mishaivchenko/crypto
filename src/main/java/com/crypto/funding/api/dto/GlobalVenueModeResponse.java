package com.crypto.funding.api.dto;

import com.crypto.funding.domain.venue.VenueAccessMode;

import java.util.List;

public record GlobalVenueModeResponse(
    VenueAccessMode mode,
    boolean modeOverridden,
    List<VenueAccessMode> availableModes
)
{
}
