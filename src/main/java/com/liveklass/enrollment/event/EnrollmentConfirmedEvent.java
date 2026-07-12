package com.liveklass.enrollment.event;

// 알림 이벤트 지점(결제 확정) — 발행만 하고 구독은 phase-3에서 연결. phase-2에서 SaleRecord 자동 생성 연결(B-1)
public record EnrollmentConfirmedEvent(
		String enrollmentId,
		String courseId,
		String studentId
) {
}
