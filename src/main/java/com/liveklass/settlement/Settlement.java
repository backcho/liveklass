package com.liveklass.settlement;

import com.liveklass.common.entity.BaseTimeEntity;
import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "settlements",
		indexes = @Index(name = "idx_settlement_creator_period", columnList = "creator_id, period_start"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseTimeEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "creator_id", nullable = false, length = 36)
	private String creatorId;

	@Column(name = "admin_id", nullable = false, length = 36)
	private String adminId;

	@Column(name = "period_start", nullable = false)
	private LocalDate periodStart;

	@Column(name = "period_end", nullable = false)
	private LocalDate periodEnd;

	@Column(name = "total_sales_amount", nullable = false)
	private long totalSalesAmount;

	@Column(name = "refund_amount", nullable = false)
	private long refundAmount;

	@Column(name = "net_sales_amount", nullable = false)
	private long netSalesAmount;

	@Column(name = "commission_amount", nullable = false)
	private long commissionAmount;

	@Column(name = "payout_amount", nullable = false)
	private long payoutAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SettlementStatus status;

	private LocalDateTime confirmedAt;

	private LocalDateTime paidAt;

	@Builder
	private Settlement(String creatorId, String adminId, LocalDate periodStart, LocalDate periodEnd,
			long totalSalesAmount, long refundAmount, long netSalesAmount, long commissionAmount,
			long payoutAmount) {
		this.id = UUID.randomUUID().toString();
		this.creatorId = creatorId;
		this.adminId = adminId;
		this.periodStart = periodStart;
		this.periodEnd = periodEnd;
		this.totalSalesAmount = totalSalesAmount;
		this.refundAmount = refundAmount;
		this.netSalesAmount = netSalesAmount;
		this.commissionAmount = commissionAmount;
		this.payoutAmount = payoutAmount;
		this.status = SettlementStatus.PENDING;
	}

	// B-5: PENDING → CONFIRMED → PAID
	public void changeStatus(SettlementStatus target, LocalDateTime now) {
		if (!status.canTransitionTo(target)) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"허용되지 않는 상태 전이입니다: " + status + " → " + target);
		}
		this.status = target;
		switch (target) {
			case CONFIRMED -> this.confirmedAt = now;
			case PAID -> this.paidAt = now;
			default -> { }
		}
	}
}
