package com.liveklass.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대기 순번 (A-6)")
public record WaitlistPositionResponse(
		String enrollmentId,
		@Schema(example = "course-1") String courseId,
		@Schema(description = "내 순번 (1부터)", example = "2") long position,
		@Schema(description = "전체 대기 인원", example = "5") long waitingCount
) {
}
