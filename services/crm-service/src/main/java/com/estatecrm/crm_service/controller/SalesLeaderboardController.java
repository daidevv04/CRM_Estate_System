package com.estatecrm.crm_service.controller;

import com.estatecrm.crm_service.dto.LeaderboardDtos.SalesLeaderboardSummary;
import com.estatecrm.crm_service.service.SalesLeaderboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
public class SalesLeaderboardController {

    private final SalesLeaderboardService leaderboardService;

    public SalesLeaderboardController(SalesLeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/leaderboard")
    public SalesLeaderboardSummary getLeaderboard(
            @RequestParam(required = false, defaultValue = "MONTH") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return leaderboardService.getLeaderboard(period, year, month, null);
    }
}
