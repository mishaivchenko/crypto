package com.crypto.funding.api;

import com.crypto.funding.api.dto.AutoApprovalRuleRequest;
import com.crypto.funding.api.dto.AutoApprovalRuleResponse;
import com.crypto.funding.application.autoapproval.AutoApprovalRuleService;
import com.crypto.funding.application.autoapproval.CreateAutoApprovalRuleCommand;
import com.crypto.funding.application.autoapproval.UpdateAutoApprovalRuleCommand;
import com.crypto.funding.config.AutoApprovalProperties;
import com.crypto.funding.domain.autoapproval.AutoApprovalRule;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auto-approval")
public class AutoApprovalController
{
    private final AutoApprovalRuleService ruleService;
    private final AutoApprovalProperties properties;

    public AutoApprovalController( AutoApprovalRuleService ruleService, AutoApprovalProperties properties )
    {
        this.ruleService = ruleService;
        this.properties = properties;
    }

    @GetMapping("/status")
    public Map<String, Object> status()
    {
        List<AutoApprovalRule> activeRules = ruleService.listActive();
        return Map.of(
            "enabled", properties.isEnabled(),
            "activeRulesCount", activeRules.size()
        );
    }

    @PostMapping("/enable")
    public Map<String, Object> enable()
    {
        properties.setEnabled( true );
        return Map.of( "enabled", true );
    }

    @PostMapping("/disable")
    public Map<String, Object> disable()
    {
        properties.setEnabled( false );
        return Map.of( "enabled", false );
    }

    @GetMapping("/rules")
    public List<AutoApprovalRuleResponse> list()
    {
        return ruleService.list()
                          .stream()
                          .map( AutoApprovalRuleResponse::from )
                          .toList();
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public AutoApprovalRuleResponse create( @Valid @RequestBody AutoApprovalRuleRequest request )
    {
        AutoApprovalRule rule = ruleService.create( new CreateAutoApprovalRuleCommand(
            request.name(),
            request.enabled(),
            request.mode(),
            request.minFundingRatePct(),
            request.maxFundingRatePct(),
            request.allowedVenues(),
            request.allowedAiRecommendations(),
            request.minAiConfidence(),
            request.allowedLiquidityScores(),
            request.defaultNotionalUsd(),
            request.defaultSide(),
            request.action(),
            request.priority(),
            request.notes()
        ) );
        return AutoApprovalRuleResponse.from( rule );
    }

    @PutMapping("/rules/{id}")
    public AutoApprovalRuleResponse update( @PathVariable Long id, @Valid @RequestBody AutoApprovalRuleRequest request )
    {
        AutoApprovalRule rule = ruleService.update( id, new UpdateAutoApprovalRuleCommand(
            request.name(),
            request.enabled(),
            request.mode(),
            request.minFundingRatePct(),
            request.maxFundingRatePct(),
            request.allowedVenues(),
            request.allowedAiRecommendations(),
            request.minAiConfidence(),
            request.allowedLiquidityScores(),
            request.defaultNotionalUsd(),
            request.defaultSide(),
            request.action(),
            request.priority(),
            request.notes()
        ) );
        return AutoApprovalRuleResponse.from( rule );
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete( @PathVariable Long id )
    {
        ruleService.delete( id );
    }

    @PostMapping("/rules/{id}/enable")
    public AutoApprovalRuleResponse enableRule( @PathVariable Long id )
    {
        return AutoApprovalRuleResponse.from( ruleService.enable( id ) );
    }

    @PostMapping("/rules/{id}/disable")
    public AutoApprovalRuleResponse disableRule( @PathVariable Long id )
    {
        return AutoApprovalRuleResponse.from( ruleService.disable( id ) );
    }
}
