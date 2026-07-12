package com.liveklass.course.dto;

import com.liveklass.course.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "강의 상태 변경 요청")
public record CourseStatusChangeRequest(
		@Schema(description = "목표 상태 (DRAFT→OPEN, OPEN→CLOSED, CLOSED→OPEN)", example = "OPEN")
		@NotNull CourseStatus status
) {
}
