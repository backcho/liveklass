package com.liveklass.commission.api;

import com.liveklass.auth.AuthUser;
import com.liveklass.commission.CommissionRateService;
import com.liveklass.commission.dto.CommissionRateCreateRequest;
import com.liveklass.commission.dto.CommissionRateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// /api/admin/** 은 SecurityConfig에서 ADMIN 전용
@Tag(name = "CommissionRate", description = "수수료율 관리 (운영자)")
@RestController
@RequestMapping("/api/admin/commission-rates")
@RequiredArgsConstructor
public class CommissionRateController {

	private final CommissionRateService commissionRateService;

	@Operation(summary = "수수료율 등록", description = "creatorId null이면 전체 기본 요율. 동일 대상 기간 겹침 거부 (B-3)")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CommissionRateResponse create(@AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody CommissionRateCreateRequest request) {
		return commissionRateService.create(authUser.getId(), request);
	}

	@Operation(summary = "수수료율 목록", description = "creatorId 지정 시 개별 이력, 미지정 시 전체 기본 이력")
	@GetMapping
	public List<CommissionRateResponse> list(@RequestParam(required = false) String creatorId) {
		return commissionRateService.list(creatorId);
	}
}
