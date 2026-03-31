package com.crypto.funding.application.port;

import com.crypto.funding.domain.venue.InstrumentMetadata;

import java.io.IOException;
import java.util.List;

public interface VenueMetadataPort
{
    String venue();

    List<InstrumentMetadata> fetchPerpetualInstruments() throws IOException, InterruptedException;
}
