package com.liveklass.enrollment.event;

import java.time.LocalDateTime;

// 알림 이벤트 지점(대기열 승격) — 발행만 하고 구독은 phase-3에서 연결
public record WaitlistPromotedEvent(
		String enrollmentId,
		String courseId,
		String studentId,
		LocalDateTime paymentDueAt
) {
}
