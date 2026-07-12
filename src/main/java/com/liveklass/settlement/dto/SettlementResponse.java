package com.liveklass.settlement.dto;

import com.liveklass.settlement.Settlement;
import com.liveklass.settlement.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "정산 이력 (B-5)")
public record SettlementResponse(
		String id,
		@Schema(example = "creator-1") String creatorId,
		@Schema(example = "admin-1") String adminId,
		LocalDate periodStart,
		LocalDate periodEnd,
		long totalSalesAmount,
		long refundAmount,
		long netSalesAmount,
		long commissionAmount,
		long payoutAmount,
		@Schema(example = "PENDING") SettlementStatus status,
		LocalDateTime confirmedAt,
		LocalDateTime paidAt
) {

	public static SettlementResponse from(Settlement s) {
		return new SettlementResponse(s.getId(), s.getCreatorId(), s.getAdminId(), s.getPeriodStart(),
				s.getPeriodEnd(), s.getTotalSalesAmount(), s.getRefundAmount(), s.getNetSalesAmount(),
				s.getCommissionAmount(), s.getPayoutAmount(), s.getStatus(), s.getConfirmedAt(), s.getPaidAt());
	}
}
