package com.liveklass.notification;

public enum NotificationType {
	ENROLLMENT_APPLIED,   // 수강 신청 완료 (대기열 편입 포함)
	ENROLLMENT_CONFIRMED, // 결제 확정
	ENROLLMENT_CANCELLED, // 취소 처리
	WAITLIST_PROMOTED,    // 대기열 승격
	GENERAL               // API 직접 요청용
}
