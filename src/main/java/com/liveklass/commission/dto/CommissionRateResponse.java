package com.liveklass.commission.dto;

import com.liveklass.commission.CommissionRate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "수수료율")
public record CommissionRateResponse(
		String id,
		@Schema(description = "null이면 전체 기본 요율", example = "creator-1") String creatorId,
		@Schema(example = "admin-1") String adminId,
		@Schema(example = "15.00") BigDecimal rate,
		LocalDate startedAt,
		@Schema(description = "null이면 현재 유효") LocalDate endedAt
) {

	public static CommissionRateResponse from(CommissionRate rate) {
		return new CommissionRateResponse(rate.getId(), rate.getCreatorId(), rate.getAdminId(),
				rate.getRate(), rate.getStartedAt(), rate.getEndedAt());
	}
}
