package com.crypto.funding.api;

import com.crypto.funding.application.ai.AiAdvisorPerformanceService;
import com.crypto.funding.config.DeepSeekProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiAdvisorController
{
    private final DeepSeekProperties deepSeekProperties;
    private final AiAdvisorPerformanceService performanceService;

    public AiAdvisorController( DeepSeekProperties deepSeekProperties, AiAdvisorPerformanceService performanceService )
    {
        this.deepSeekProperties = deepSeekProperties;
        this.performanceService = performanceService;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus()
    {
        return Map.of( "enabled", deepSeekProperties.isEnabled() );
    }

    @PostMapping("/enable")
    public Map<String, Object> enable()
    {
        deepSeekProperties.setEnabled( true );
        return Map.of( "enabled", true );
    }

    @PostMapping("/disable")
    public Map<String, Object> disable()
    {
        deepSeekProperties.setEnabled( false );
        return Map.of( "enabled", false );
    }

    @GetMapping("/performance")
    public AiAdvisorPerformanceService.PerformanceStats getPerformance()
    {
        return performanceService.getPerformanceStats();
    }
}
