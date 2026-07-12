package com.liveklass.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "공통 에러 응답")
public record ErrorResponse(
		@Schema(description = "에러 코드", example = "INVALID_REQUEST") String code,
		@Schema(description = "에러 메시지") String message,
		@Schema(description = "필드 단위 상세 (검증 실패 시)") List<FieldErrorDetail> errors
) {

	public record FieldErrorDetail(String field, String reason) {
	}

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of());
	}

	public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorDetail> errors) {
		return new ErrorResponse(errorCode.name(), errorCode.getMessage(), errors);
	}
}
