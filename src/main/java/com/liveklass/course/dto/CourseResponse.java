package com.liveklass.course.dto;

import com.liveklass.course.Course;
import com.liveklass.course.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "강의 정보")
public record CourseResponse(
		@Schema(example = "course-1") String id,
		@Schema(example = "creator-1") String creatorId,
		@Schema(example = "Spring Boot 입문") String title,
		String description,
		@Schema(example = "50000") int price,
		@Schema(description = "수강 정원", example = "10") int capacity,
		@Schema(description = "현재 신청(확정) 인원 — confirmed_count 기준 (A-1)", example = "3") int confirmedCount,
		@Schema(example = "OPEN") CourseStatus status,
		LocalDateTime startDate,
		LocalDateTime endDate
) {

	public static CourseResponse from(Course course) {
		return new CourseResponse(course.getId(), course.getCreatorId(), course.getTitle(), course.getDescription(),
				course.getPrice(), course.getCapacity(), course.getConfirmedCount(), course.getStatus(),
				course.getStartDate(), course.getEndDate());
	}
}
