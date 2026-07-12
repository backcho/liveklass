package com.liveklass.settlement.dto;

import com.liveklass.settlement.Settlement;
import com.liveklass.settlement.SettlementDetail;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "정산 이력 + 상세 라인 (B-6: 판매 +, 환불 − — 라인 합계 = 순 판매)")
public record SettlementWithDetailsResponse(
		SettlementResponse settlement,
		List<DetailLine> details
) {

	@Schema(description = "정산 상세 라인")
	public record DetailLine(
			@Schema(example = "SALE") SettlementDetail.RecordType recordType,
			String saleRecordId,
			String cancelRecordId,
			@Schema(description = "SALE 양수 / CANCEL 음수", example = "50000") long amount
	) {

		public static DetailLine from(SettlementDetail d) {
			return new DetailLine(d.getRecordType(), d.getSaleRecordId(), d.getCancelRecordId(), d.getAmount());
		}
	}

	public static SettlementWithDetailsResponse of(Settlement settlement, List<SettlementDetail> details) {
		return new SettlementWithDetailsResponse(SettlementResponse.from(settlement),
				details.stream().map(DetailLine::from).toList());
	}
}
