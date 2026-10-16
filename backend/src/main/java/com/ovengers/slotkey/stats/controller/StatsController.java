package com.ovengers.slotkey.stats.controller;

import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.stats.dto.response.PublicLocationRankingResponse;
import com.ovengers.slotkey.stats.dto.response.PublicSpaceRankingResponse;
import com.ovengers.slotkey.stats.entity.RankingMetric;
import com.ovengers.slotkey.stats.entity.RankingPeriodType;
import com.ovengers.slotkey.stats.service.SpaceRankingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 일반 회원용 랭킹 API. 로그인한 회원이면 누구나 볼 수 있고(SecurityConfig 기본 규칙), 같은 확정 스냅샷을 쓰되
 * 순매출은 내려주지 않으며 NET_REVENUE 정렬도 거절한다(정렬 순서로 매출 순위가 드러나지 않게). 명세: api-spec §14-2.
 */
@Tag(name = "통계", description = "베스트 공간 랭킹(매일 00:01 확정값) 조회 API. 순매출은 제외")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final SpaceRankingQueryService queryService;

    public StatsController(SpaceRankingQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(summary = "공간별 베스트 랭킹(회원용)",
            description = "정렬 기준은 RESERVATION_COUNT(기본) 또는 SLOT_COUNT. 응답에 순매출이 없다.")
    @GetMapping("/space-ranking")
    public ResponseEntity<ApiResponse<PublicSpaceRankingResponse>> getSpaceRanking(
            @Parameter(description = "DAILY | WEEKLY | MONTHLY (필수)")
            @RequestParam(required = false) RankingPeriodType period,
            @Parameter(description = "조회할 날짜(yyyy-MM-dd). 그 날짜를 포함하는 달력 기간(KST, 주는 월요일 시작) (필수)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "정렬 기준. RESERVATION_COUNT(기본) | SLOT_COUNT")
            @RequestParam(required = false) RankingMetric metric,
            @Parameter(description = "지역 필터: 판교 | 강남 | 하남")
            @RequestParam(required = false) String location,
            @Parameter(description = "최대 개수. 기본 10, 최대 50")
            @RequestParam(defaultValue = "10") int limit
    ) {
        requirePeriodAndDate(period, date);
        return ResponseEntity.ok(ApiResponse.success(PublicSpaceRankingResponse.from(
                queryService.getSpaceRanking(period, date, publicMetric(metric), location, limit))));
    }

    @Operation(summary = "지역별 베스트 랭킹(회원용)", description = "공간 랭킹과 같은 규칙. 응답에 순매출이 없다.")
    @GetMapping("/location-ranking")
    public ResponseEntity<ApiResponse<PublicLocationRankingResponse>> getLocationRanking(
            @RequestParam(required = false) RankingPeriodType period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) RankingMetric metric,
            @RequestParam(defaultValue = "10") int limit
    ) {
        requirePeriodAndDate(period, date);
        return ResponseEntity.ok(ApiResponse.success(PublicLocationRankingResponse.from(
                queryService.getLocationRanking(period, date, publicMetric(metric), limit))));
    }

    private static void requirePeriodAndDate(RankingPeriodType period, LocalDate date) {
        if (period == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "period는 필수입니다.");
        }
        if (date == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "date는 필수입니다.");
        }
    }

    private static RankingMetric publicMetric(RankingMetric metric) {
        if (metric == null) {
            return RankingMetric.RESERVATION_COUNT;
        }
        if (metric == RankingMetric.NET_REVENUE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "NET_REVENUE 정렬은 지원하지 않습니다.");
        }
        return metric;
    }
}
