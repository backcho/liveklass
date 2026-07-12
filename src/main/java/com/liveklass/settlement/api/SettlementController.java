package com.liveklass.settlement.api;

import com.liveklass.auth.AuthUser;
import com.liveklass.common.dto.PageResponse;
import com.liveklass.settlement.SettlementService;
import com.liveklass.settlement.dto.MonthlySettlementResponse;
import com.liveklass.settlement.dto.SettlementAggregateResponse;
import com.liveklass.settlement.dto.SettlementCreateRequest;
import com.liveklass.settlement.dto.SettlementResponse;
import com.liveklass.settlement.dto.SettlementStatusChangeRequest;
import com.liveklass.settlement.dto.SettlementWithDetailsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Settlement", description = "정산")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettlementController {

	private final SettlementService settlementService;

	@Operation(summary = "크리에이터 월별 정산 조회",
			description = "실시간 집계. CREATOR는 본인만, ADMIN은 전체. 빈 월 0원 (B-4), 형식 오류 400, 미래 연월 허용 (B-4a)")
	@GetMapping("/settlements/monthly")
	public MonthlySettlementResponse monthly(@AuthenticationPrincipal AuthUser authUser,
			@RequestParam String creatorId,
			@RequestParam String month) {
		return settlementService.monthly(authUser, creatorId, month);
	}

	@Operation(summary = "정산 집계", description = "운영자 전용 — 기간 내 크리에이터별 정산 예정 금액 + 전체 합계")
	@GetMapping("/admin/settlements/aggregate")
	public SettlementAggregateResponse aggregate(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return settlementService.aggregate(from, to);
	}

	@Operation(summary = "정산 확정(생성)",
			description = "운영자 전용 — SETTLEMENT + 상세(±라인) 생성, PENDING 시작. 동일 크리에이터 기간 겹침 거부 (B-5/B-6)")
	@PostMapping("/admin/settlements")
	@ResponseStatus(HttpStatus.CREATED)
	public SettlementWithDetailsResponse confirmSettlement(@AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody SettlementCreateRequest request) {
		return settlementService.confirmSettlement(authUser.getId(), request);
	}

	@Operation(summary = "정산 상태 변경", description = "운영자 전용 — PENDING→CONFIRMED→PAID (B-5)")
	@PostMapping("/admin/settlements/{settlementId}/status")
	public SettlementResponse changeStatus(@PathVariable String settlementId,
			@Valid @RequestBody SettlementStatusChangeRequest request) {
		return settlementService.changeStatus(settlementId, request.status());
	}

	@Operation(summary = "내 정산 목록", description = "CREATOR 전용 — 확정된 과거 정산 이력")
	@GetMapping("/settlements/my")
	@PreAuthorize("hasRole('CREATOR')")
	public PageResponse<SettlementResponse> mySettlements(@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return settlementService.mySettlements(authUser.getId(),
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "periodStart")));
	}

	@Operation(summary = "정산 상세 조회", description = "본인(CREATOR) 또는 ADMIN — 상세 라인 포함, 라인 합계 = 순 판매 (B-6)")
	@GetMapping("/settlements/{settlementId}")
	public SettlementWithDetailsResponse detail(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable String settlementId) {
		return settlementService.detail(authUser, settlementId);
	}
}
