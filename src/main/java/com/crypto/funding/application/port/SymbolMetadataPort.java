package com.crypto.funding.application.port;

import java.util.Optional;

public interface SymbolMetadataPort
{
    Optional<SymbolMetadata> findSymbolMetadata( String venue, String symbol );
}
