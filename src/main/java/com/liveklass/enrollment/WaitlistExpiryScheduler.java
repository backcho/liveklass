package com.liveklass.enrollment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A-6: 승격 결제 기한(24h) 만료 건을 자동 취소하고 다음 순번을 승격.
 * 건별 독립 트랜잭션 — 한 건 실패가 나머지를 막지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitlistExpiryScheduler {

	private final EnrollmentService enrollmentService;

	@Scheduled(fixedDelayString = "${liveklass.waitlist.expiry-check-delay:60s}")
	public void expireOverduePromotions() {
		for (String enrollmentId : enrollmentService.findOverduePromotionIds()) {
			try {
				enrollmentService.expirePromotion(enrollmentId);
			} catch (Exception e) {
				log.warn("승격 기한 만료 처리 실패: enrollmentId={}", enrollmentId, e);
			}
		}
	}
}
