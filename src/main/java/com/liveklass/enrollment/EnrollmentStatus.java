package com.liveklass.enrollment;

import java.util.Set;

public enum EnrollmentStatus {
	PENDING, CONFIRMED, CANCELLED, WAITLISTED;

	// A-2a: 활성 = 중복 신청을 막는 상태 (active_flag generated column과 정의 일치 필수)
	public static final Set<EnrollmentStatus> ACTIVE_STATUSES = Set.of(PENDING, CONFIRMED, WAITLISTED);
}
