package com.liveklass.notification;

/**
 * C-2: PENDING → PROCESSING → SENT | RETRY_WAIT | DEAD, RETRY_WAIT → PROCESSING,
 * DEAD → PENDING(수동 재시도, C-7). PROCESSING은 "지금 워커가 잡고 있음"만 의미.
 */
public enum NotificationStatus {
	PENDING, PROCESSING, SENT, RETRY_WAIT, DEAD
}
