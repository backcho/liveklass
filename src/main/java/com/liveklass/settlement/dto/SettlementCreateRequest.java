package com.liveklass.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "정산 확정(생성) 요청 — 운영자 전용 (B-5)")
public record SettlementCreateRequest(
		@Schema(example = "creator-1") @NotBlank String creatorId,
		@Schema(example = "2025-03-01") @NotNull LocalDate periodStart,
		@Schema(example = "2025-03-31") @NotNull LocalDate periodEnd
) {
}
