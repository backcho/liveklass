package com.liveklass.sale.dto;

import com.liveklass.sale.CancelRecord;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "취소(환불) 내역")
public record CancelRecordResponse(
		@Schema(example = "cancel-1") String id,
		@Schema(example = "sale-3") String saleRecordId,
		@Schema(example = "80000") int refundAmount,
		LocalDateTime cancelledAt
) {

	public static CancelRecordResponse from(CancelRecord cancel) {
		return new CancelRecordResponse(cancel.getId(), cancel.getSaleRecordId(),
				cancel.getRefundAmount(), cancel.getCancelledAt());
	}
}
