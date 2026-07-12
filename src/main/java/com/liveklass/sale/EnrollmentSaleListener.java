package com.liveklass.sale;

import com.liveklass.enrollment.EnrollmentStatus;
import com.liveklass.enrollment.event.EnrollmentCancelledEvent;
import com.liveklass.enrollment.event.EnrollmentConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * B-1: 결제 확정 → SaleRecord, CONFIRMED 취소 → CancelRecord 자동 생성.
 * 동기 @EventListener — 확정/취소 트랜잭션에 참여해 원자성 보장.
 */
@Component
@RequiredArgsConstructor
public class EnrollmentSaleListener {

	private final SaleService saleService;
	private final Clock clock;

	@EventListener
	public void onConfirmed(EnrollmentConfirmedEvent event) {
		saleService.createFromEnrollment(event.enrollmentId(), event.courseId(), event.studentId(),
				LocalDateTime.now(clock));
	}

	@EventListener
	public void onCancelled(EnrollmentCancelledEvent event) {
		if (event.previousStatus() != EnrollmentStatus.CONFIRMED) {
			return; // PENDING/WAITLISTED 취소는 판매가 없음
		}
		saleService.refundRemainingForEnrollment(event.enrollmentId(), LocalDateTime.now(clock));
	}
}
