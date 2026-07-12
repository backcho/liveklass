package com.liveklass.enrollment.event;

import com.liveklass.enrollment.EnrollmentStatus;

// 알림 이벤트 지점(취소) — 발행만 하고 구독은 phase-3에서 연결. phase-2에서 CancelRecord 자동 생성 연결(B-1)
public record EnrollmentCancelledEvent(
		String enrollmentId,
		String courseId,
		String studentId,
		EnrollmentStatus previousStatus
) {
}
