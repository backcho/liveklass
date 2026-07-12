package com.liveklass.settlement;

import com.liveklass.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * B-6: 정산 상세 — 판매(+)와 환불(−) 라인 모두 포함. 상세 합계 = 순 판매 재현.
 */
@Entity
@Table(name = "settlement_details",
		indexes = @Index(name = "idx_settlement_detail", columnList = "settlement_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementDetail extends BaseTimeEntity {

	public enum RecordType { SALE, CANCEL }

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "settlement_id", nullable = false, length = 36)
	private String settlementId;

	@Enumerated(EnumType.STRING)
	@Column(name = "record_type", nullable = false, length = 10)
	private RecordType recordType;

	@Column(name = "sale_record_id", length = 36)
	private String saleRecordId;

	@Column(name = "cancel_record_id", length = 36)
	private String cancelRecordId;

	// SALE은 양수, CANCEL은 음수
	@Column(nullable = false)
	private long amount;

	private SettlementDetail(String settlementId, RecordType recordType, String saleRecordId,
			String cancelRecordId, long amount) {
		this.id = UUID.randomUUID().toString();
		this.settlementId = settlementId;
		this.recordType = recordType;
		this.saleRecordId = saleRecordId;
		this.cancelRecordId = cancelRecordId;
		this.amount = amount;
	}

	public static SettlementDetail saleLine(String settlementId, String saleRecordId, long amount) {
		return new SettlementDetail(settlementId, RecordType.SALE, saleRecordId, null, amount);
	}

	public static SettlementDetail cancelLine(String settlementId, String cancelRecordId, long refundAmount) {
		return new SettlementDetail(settlementId, RecordType.CANCEL, null, cancelRecordId, -refundAmount);
	}
}
