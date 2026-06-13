package com.zhishu.governance;

import com.zhishu.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/usage")
@RequiredArgsConstructor
public class UsageController {

    private final TokenUsageLogRepository repository;

    @GetMapping("/daily")
    public ApiResponse<List<TokenUsageLogRepository.DailyUsage>> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(repository.aggregate(date.atStartOfDay(), date.plusDays(1).atStartOfDay()));
    }
}
