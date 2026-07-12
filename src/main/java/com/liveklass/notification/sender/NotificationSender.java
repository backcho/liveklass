package com.liveklass.notification.sender;

import com.liveklass.notification.NotificationRequest;

/**
 * C-1: 발송부 추상화 — 실브로커(SQS 등)·실제 메일 발송으로 전환 시 이 구현만 교체.
 * 실패는 예외로 알린다 (잡이 재시도 정책 적용).
 */
public interface NotificationSender {

	void send(NotificationRequest request);
}
