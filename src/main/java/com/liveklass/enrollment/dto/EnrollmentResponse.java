package com.liveklass.enrollment.dto;

import com.liveklass.enrollment.Enrollment;
import com.liveklass.enrollment.EnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "수강 신청 정보")
public record EnrollmentResponse(
		String id,
		@Schema(example = "course-1") String courseId,
		@Schema(description = "강의명 (목록 조회 시 포함)", example = "Spring Boot 입문") String courseTitle,
		@Schema(example = "student-1") String studentId,
		@Schema(description = "수강생 이름 (크리에이터용 목록 조회 시 포함)", example = "수강생-student-1") String studentName,
		@Schema(example = "PENDING") EnrollmentStatus status,
		LocalDateTime appliedAt,
		LocalDateTime confirmedAt,
		LocalDateTime cancelledAt,
		@Schema(description = "대기열 승격 건의 결제 기한 (A-6)") LocalDateTime paymentDueAt
) {

	public static EnrollmentResponse from(Enrollment e) {
		return of(e, null, null);
	}

	public static EnrollmentResponse of(Enrollment e, String courseTitle, String studentName) {
		return new EnrollmentResponse(e.getId(), e.getCourseId(), courseTitle, e.getStudentId(), studentName,
				e.getStatus(), e.getAppliedAt(), e.getConfirmedAt(), e.getCancelledAt(), e.getPaymentDueAt());
	}
}
