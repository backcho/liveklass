package com.liveklass.sale.api;

import com.liveklass.auth.AuthUser;
import com.liveklass.common.dto.PageResponse;
import com.liveklass.sale.SaleService;
import com.liveklass.sale.dto.CancelCreateRequest;
import com.liveklass.sale.dto.CancelRecordResponse;
import com.liveklass.sale.dto.SaleCreateRequest;
import com.liveklass.sale.dto.SaleRecordResponse;
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

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "2-1. [과제B] Sale", description = "판매·취소 내역")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SaleController {

	private final SaleService saleService;

	@Operation(summary = "판매 내역 등록", description = "운영자 전용 — 샘플 주입·독립 사용 (B-1). 요율은 판매 시점 스냅샷 (B-3)")
	@PostMapping("/admin/sales")
	@ResponseStatus(HttpStatus.CREATED)
	public SaleRecordResponse create(@Valid @RequestBody SaleCreateRequest request) {
		return saleService.create(request);
	}

	@Operation(summary = "취소(환불) 내역 등록", description = "운영자 전용. 누적 환불 ≤ 원 결제액 (B-2)")
	@PostMapping("/admin/sales/{saleRecordId}/cancels")
	@ResponseStatus(HttpStatus.CREATED)
	public CancelRecordResponse registerCancel(@PathVariable String saleRecordId,
			@Valid @RequestBody CancelCreateRequest request) {
		return saleService.registerCancel(saleRecordId, request);
	}

	@Operation(summary = "판매 목록 (운영자)", description = "전체 판매 내역, 기간 필터(paid_at 기준)")
	@GetMapping("/admin/sales")
	public PageResponse<SaleRecordResponse> allSales(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return saleService.allSales(from, to,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paidAt")));
	}

	@Operation(summary = "판매 상세 (운영자)")
	@GetMapping("/admin/sales/{saleRecordId}")
	public SaleRecordResponse getSale(@PathVariable String saleRecordId) {
		return saleService.getSale(saleRecordId);
	}

	@Operation(summary = "판매 건의 취소/환불 이력 (운영자)")
	@GetMapping("/admin/sales/{saleRecordId}/cancels")
	public List<CancelRecordResponse> cancelsOfSale(@PathVariable String saleRecordId) {
		return saleService.cancelsOfSale(saleRecordId);
	}

	@Operation(summary = "내 강의 판매 목록", description = "CREATOR 전용, 기간 필터(paid_at 기준)")
	@GetMapping("/sales/my")
	@PreAuthorize("hasRole('CREATOR')")
	public PageResponse<SaleRecordResponse> mySales(@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return saleService.mySales(authUser.getId(), from, to,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paidAt")));
	}

	@Operation(summary = "내 강의 환불(취소) 목록", description = "CREATOR 전용, 기간 필터(cancelled_at 기준)")
	@GetMapping("/sales/my/cancels")
	@PreAuthorize("hasRole('CREATOR')")
	public PageResponse<CancelRecordResponse> myCancels(@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return saleService.myCancels(authUser.getId(), from, to,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "cancelledAt")));
	}
}
