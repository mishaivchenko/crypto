package com.crypto.funding.telegram.client;

import com.crypto.funding.telegram.client.dto.ArmedTradeSummary;
import com.crypto.funding.telegram.client.dto.CandidateSummary;
import com.crypto.funding.telegram.client.dto.MonitorOverview;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "monitor-api", url = "${monitor.base-url}")
public interface MonitorApiClient
{
    @GetMapping("/api/v1/candidates")
    PageResponse<CandidateSummary> getCandidates(
        @RequestParam(required = false) String status,
        @RequestParam(required = false, defaultValue = "20") int size,
        @RequestParam(required = false, defaultValue = "0") int page
    );

    @GetMapping("/api/v1/armed-trades")
    List<ArmedTradeSummary> getArmedTrades(
        @RequestParam(required = false, defaultValue = "false") boolean includeHistorical
    );

    @GetMapping("/api/v2/monitor/overview")
    MonitorOverview getOverview();
}
