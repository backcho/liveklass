package com.liveklass.enrollment.event;

import com.liveklass.enrollment.EnrollmentStatus;

import java.time.LocalDateTime;

// 알림 이벤트 지점(신청 완료) — 발행만 하고 구독은 phase-3에서 연결
public record EnrollmentAppliedEvent(
		String enrollmentId,
		String courseId,
		String studentId,
		EnrollmentStatus status,
		LocalDateTime appliedAt
) {
}
