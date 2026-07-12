package com.liveklass.course.dto;

import com.liveklass.course.Course;
import com.liveklass.course.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "강의 상세 — 현재 신청 인원은 confirmedCount, 참고용으로 대기 수 병기 (A-1)")
public record CourseDetailResponse(
		@Schema(example = "course-1") String id,
		@Schema(example = "creator-1") String creatorId,
		@Schema(example = "Spring Boot 입문") String title,
		String description,
		@Schema(example = "50000") int price,
		@Schema(description = "수강 정원", example = "10") int capacity,
		@Schema(description = "현재 신청(확정) 인원", example = "3") int confirmedCount,
		@Schema(description = "잔여석", example = "7") int remainingSeats,
		@Schema(description = "결제 대기(PENDING) 수 — 정원 미점유", example = "2") long pendingCount,
		@Schema(description = "대기열(WAITLISTED) 수", example = "0") long waitlistedCount,
		@Schema(example = "OPEN") CourseStatus status,
		LocalDateTime startDate,
		LocalDateTime endDate
) {

	public static CourseDetailResponse of(Course course, long pendingCount, long waitlistedCount) {
		return new CourseDetailResponse(course.getId(), course.getCreatorId(), course.getTitle(),
				course.getDescription(), course.getPrice(), course.getCapacity(), course.getConfirmedCount(),
				Math.max(0, course.getCapacity() - course.getConfirmedCount()), pendingCount, waitlistedCount,
				course.getStatus(), course.getStartDate(), course.getEndDate());
	}
}
