package com.liveklass.commission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "수수료율 등록 요청 (B-3)")
public record CommissionRateCreateRequest(
		@Schema(description = "대상 크리에이터 — null이면 전체 기본 요율", example = "creator-1") String creatorId,
		@Schema(description = "퍼센트", example = "15.00")
		@NotNull @DecimalMin("0.01") @DecimalMax("99.99") BigDecimal rate,
		@Schema(description = "적용 시작일", example = "2026-08-01") @NotNull LocalDate startedAt,
		@Schema(description = "적용 마감일 — null이면 계속 유효", example = "2026-12-31") LocalDate endedAt
) {
}
