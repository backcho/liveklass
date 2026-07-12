package com.liveklass.course;

public enum CourseStatus {
	DRAFT, OPEN, CLOSED;

	// A-5: DRAFT→OPEN(수동) / OPEN→CLOSED(수동 + 만석 자동) / CLOSED→OPEN(수동 재오픈)
	public boolean canTransitionTo(CourseStatus target) {
		return switch (this) {
			case DRAFT -> target == OPEN;
			case OPEN -> target == CLOSED;
			case CLOSED -> target == OPEN;
		};
	}
}
