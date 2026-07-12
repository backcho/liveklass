package com.liveklass.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 공통
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

	// 과제 A — 수강 신청 (phase-1)
	COURSE_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "정원이 초과되었습니다."),
	INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 상태 전이입니다."),
	DUPLICATE_ACTIVE_ENROLLMENT(HttpStatus.CONFLICT, "이미 신청한 강의입니다."),
	CANCEL_PERIOD_EXPIRED(HttpStatus.CONFLICT, "취소 가능 기간이 지났습니다."),

	// 과제 B — 정산 (phase-2)
	REFUND_EXCEEDS_SALE_AMOUNT(HttpStatus.CONFLICT, "누적 환불액이 결제 금액을 초과합니다."),
	COMMISSION_RATE_PERIOD_OVERLAP(HttpStatus.CONFLICT, "수수료율 적용 기간이 겹칩니다."),
	SETTLEMENT_PERIOD_OVERLAP(HttpStatus.CONFLICT, "이미 정산된 기간과 겹칩니다."),

	// 과제 C — 알림 (phase-3)
	NOTIFICATION_RETRY_NOT_ALLOWED(HttpStatus.CONFLICT, "재시도할 수 없는 알림 상태입니다.");

	private final HttpStatus status;
	private final String message;
}
