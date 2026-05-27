package com.crypto.funding.engine;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/engine/credentials")
public class EngineCredentialStatusController
{
    private final CredentialAwareExecutionPort executionPort;
    private final EngineProperties engineProperties;

    public EngineCredentialStatusController(
        CredentialAwareExecutionPort executionPort,
        EngineProperties engineProperties
    )
    {
        this.executionPort = executionPort;
        this.engineProperties = engineProperties;
    }

    @GetMapping("/status")
    public Map<String, Boolean> status()
    {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for( String venue : engineProperties.liveEnabledVenues() )
        {
            result.put( venue, executionPort.hasCredentials( venue ) );
        }
        return result;
    }
}
