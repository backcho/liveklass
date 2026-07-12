package com.liveklass.sale;

import com.liveklass.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cancel_records",
		indexes = {
				@Index(name = "idx_cancel_cancelled_at", columnList = "cancelled_at"),
				@Index(name = "idx_cancel_sale", columnList = "sale_record_id")
		})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CancelRecord extends BaseTimeEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "sale_record_id", nullable = false, length = 36)
	private String saleRecordId;

	// B-2: 판매 1건에 취소 N회 — 누적 합 ≤ 원 결제액은 서비스에서 검증
	@Column(name = "refund_amount", nullable = false)
	private int refundAmount;

	@Column(name = "cancelled_at", nullable = false)
	private LocalDateTime cancelledAt;

	@Builder
	private CancelRecord(String id, String saleRecordId, int refundAmount, LocalDateTime cancelledAt) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.saleRecordId = saleRecordId;
		this.refundAmount = refundAmount;
		this.cancelledAt = cancelledAt;
	}
}
