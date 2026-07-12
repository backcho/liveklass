package com.liveklass.notification;

import com.liveklass.enrollment.event.EnrollmentAppliedEvent;
import com.liveklass.enrollment.event.EnrollmentCancelledEvent;
import com.liveklass.enrollment.event.EnrollmentConfirmedEvent;
import com.liveklass.enrollment.event.WaitlistPromotedEvent;
import com.liveklass.notification.dto.NotificationCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 이벤트 발행 지점 연동: 신청 완료 / 결제 확정 / 취소 / 대기열 승격 (phase-1 이벤트 구독).
 *
 * 적재(INSERT)는 원 트랜잭션에 참여(outbox 방식) — 비즈니스 커밋과 알림 요청이 원자적.
 * "발송" 실패는 원 트랜잭션과 무관하게 잡에서 상태·사유로 기록·재시도된다 (예외 무시 아님).
 * event_id는 이벤트 종류+enrollment id의 결정적 멱등키 (C-6).
 */
@Component
@RequiredArgsConstructor
public class EnrollmentNotificationListener {

	private final NotificationService notificationService;

	@EventListener
	public void onApplied(EnrollmentAppliedEvent event) {
		enqueue("enrollment-applied:" + event.enrollmentId(), event.studentId(),
				NotificationType.ENROLLMENT_APPLIED, event.enrollmentId());
	}

	@EventListener
	public void onConfirmed(EnrollmentConfirmedEvent event) {
		enqueue("enrollment-confirmed:" + event.enrollmentId(), event.studentId(),
				NotificationType.ENROLLMENT_CONFIRMED, event.enrollmentId());
	}

	@EventListener
	public void onCancelled(EnrollmentCancelledEvent event) {
		enqueue("enrollment-cancelled:" + event.enrollmentId(), event.studentId(),
				NotificationType.ENROLLMENT_CANCELLED, event.enrollmentId());
	}

	@EventListener
	public void onPromoted(WaitlistPromotedEvent event) {
		enqueue("waitlist-promoted:" + event.enrollmentId(), event.studentId(),
				NotificationType.WAITLIST_PROMOTED, event.enrollmentId());
	}

	private void enqueue(String eventId, String recipientId, NotificationType type, String referenceId) {
		notificationService.enqueue(new NotificationCreateRequest(
				recipientId, type, NotificationChannel.IN_APP, eventId, referenceId));
	}
}
