package com.liveklass.settlement.dto;

import com.liveklass.settlement.SettlementCalculator;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "크리에이터 월별 정산 (실시간 집계). 판매 없는 월은 0원 (B-4), 월 경계 취소로 음수 가능 (B-3a)")
public record MonthlySettlementResponse(
		@Schema(example = "creator-1") String creatorId,
		@Schema(example = "2025-03") String month,
		@Schema(description = "총 판매 금액", example = "260000") long totalSalesAmount,
		@Schema(description = "취소/환불 금액", example = "110000") long refundAmount,
		@Schema(description = "순 판매 (총 판매 − 환불)", example = "150000") long netSalesAmount,
		@Schema(description = "플랫폼 수수료 (스냅샷 요율 레코드 합산)", example = "30000") long commissionAmount,
		@Schema(description = "정산 예정 금액 (순 판매 − 수수료)", example = "120000") long payoutAmount,
		@Schema(example = "4") int salesCount,
		@Schema(example = "2") int cancelCount
) {

	public static MonthlySettlementResponse of(String creatorId, String month,
			SettlementCalculator.Result result) {
		return new MonthlySettlementResponse(creatorId, month, result.totalSalesAmount(),
				result.refundAmount(), result.netSalesAmount(), result.commissionAmount(),
				result.payoutAmount(), result.salesCount(), result.cancelCount());
	}
}
