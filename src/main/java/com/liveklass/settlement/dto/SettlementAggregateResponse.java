package com.liveklass.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "운영자 정산 집계 — 기간 내 크리에이터별 정산 예정 금액 + 전체 합계")
public record SettlementAggregateResponse(
		LocalDate periodStart,
		LocalDate periodEnd,
		List<CreatorPayout> creators,
		@Schema(description = "정산 예정 금액 전체 합계") long totalPayoutAmount
) {

	@Schema(description = "크리에이터별 정산 요약")
	public record CreatorPayout(
			@Schema(example = "creator-1") String creatorId,
			@Schema(example = "김강사") String creatorName,
			long totalSalesAmount,
			long refundAmount,
			long netSalesAmount,
			long commissionAmount,
			long payoutAmount,
			int salesCount,
			int cancelCount
	) {
	}
}
