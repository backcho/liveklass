package com.liveklass.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;

@Schema(description = "강의 수정 요청 (전체 필드 교체)")
public record CourseUpdateRequest(
		@Schema(example = "Spring Boot 입문") @NotBlank String title,
		@Schema(example = "스프링 부트 기초부터 배포까지") String description,
		@Schema(example = "50000") @NotNull @PositiveOrZero Integer price,
		@Schema(description = "수강 정원 — 확정 인원보다 작게 줄일 수 없음", example = "10") @NotNull @Positive Integer capacity,
		@Schema(example = "2026-08-01T10:00:00") LocalDateTime startDate,
		@Schema(example = "2026-08-31T18:00:00") LocalDateTime endDate
) {
}
