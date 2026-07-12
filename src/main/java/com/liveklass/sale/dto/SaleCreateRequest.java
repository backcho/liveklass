package com.liveklass.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

@Schema(description = "판매 내역 등록 요청 (B-1 — 샘플 주입·독립 사용)")
public record SaleCreateRequest(
		@Schema(description = "미지정 시 UUID 발급 — 샘플 데이터(sale-1 등) 주입용", example = "sale-99") String id,
		@Schema(example = "course-1") @NotBlank String courseId,
		@Schema(example = "student-1") @NotBlank String studentId,
		@Schema(example = "50000") @NotNull @Positive Integer amount,
		@Schema(description = "결제 일시 (KST)", example = "2025-03-05T10:00:00") @NotNull LocalDateTime paidAt
) {
}
