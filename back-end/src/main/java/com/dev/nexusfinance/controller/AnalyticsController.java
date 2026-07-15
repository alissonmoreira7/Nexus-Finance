package com.dev.nexusfinance.controller;
import com.dev.nexusfinance.services.AnalyticsService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    private final com.dev.nexusfinance.services.AccountService accounts;

    public AnalyticsController(AnalyticsService service, com.dev.nexusfinance.services.AccountService accounts) { this.service = service; this.accounts = accounts; }
    @GetMapping("/{accountId}/summary")
    public AnalyticsService.AnalyticsSummary summary(@RequestAttribute UUID authenticatedUserId, @PathVariable UUID accountId) {
        accounts.assertOwnership(accountId, authenticatedUserId); return service.getSummary(accountId);
    }
}
