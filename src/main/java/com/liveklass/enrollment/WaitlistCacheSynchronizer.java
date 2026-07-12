package com.liveklass.enrollment;

import com.liveklass.enrollment.event.EnrollmentAppliedEvent;
import com.liveklass.enrollment.event.EnrollmentCancelledEvent;
import com.liveklass.enrollment.event.WaitlistPromotedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * DB 커밋 후에만 캐시를 갱신(롤백 시 캐시 오염 방지). 캐시 갱신 실패는 무시 —
 * 다음 순번 조회에서 재구축된다 (A-6: SOT는 DB).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitlistCacheSynchronizer {

	private final WaitlistCacheService waitlistCacheService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onApplied(EnrollmentAppliedEvent event) {
		if (event.status() != EnrollmentStatus.WAITLISTED) {
			return;
		}
		safely(() -> waitlistCacheService.add(event.courseId(), event.enrollmentId(), event.appliedAt()));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onCancelled(EnrollmentCancelledEvent event) {
		if (event.previousStatus() != EnrollmentStatus.WAITLISTED) {
			return;
		}
		safely(() -> waitlistCacheService.remove(event.courseId(), event.enrollmentId()));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPromoted(WaitlistPromotedEvent event) {
		safely(() -> waitlistCacheService.remove(event.courseId(), event.enrollmentId()));
	}

	private void safely(Runnable action) {
		try {
			action.run();
		} catch (Exception e) {
			log.warn("waitlist 캐시 동기화 실패 — 조회 시 재구축됨", e);
		}
	}
}
