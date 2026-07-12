package com.liveklass.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

@Schema(description = "취소(환불) 내역 등록 요청 (B-1/B-2)")
public record CancelCreateRequest(
		@Schema(description = "미지정 시 UUID 발급 — 샘플 데이터(cancel-1 등) 주입용", example = "cancel-99") String id,
		@Schema(description = "환불 금액 — 누적 합이 원 결제액을 넘을 수 없음", example = "30000")
		@NotNull @Positive Integer refundAmount,
		@Schema(description = "취소 일시 (KST)", example = "2025-03-21T10:00:00") @NotNull LocalDateTime cancelledAt
) {
}
