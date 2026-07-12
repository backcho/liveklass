package com.liveklass.settlement.dto;

import com.liveklass.settlement.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "정산 상태 변경 요청 (PENDING→CONFIRMED→PAID)")
public record SettlementStatusChangeRequest(
		@Schema(example = "CONFIRMED") @NotNull SettlementStatus status
) {
}
