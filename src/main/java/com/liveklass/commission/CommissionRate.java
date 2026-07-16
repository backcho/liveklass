package com.liveklass.commission;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "commission_rates",
		indexes = @Index(name = "idx_commission_rate_target", columnList = "creator_id, started_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionRate extends BaseTimeEntity {

	@Id
	@Column(length = 36)
	private String id;

	// B-3: null이면 전체 기본 요율. 요율 결정은 개별 크리에이터 > 전체 기본
	@Column(name = "creator_id", length = 36)
	private String creatorId;

	@Column(name = "admin_id", nullable = false, length = 36)
	private String adminId;

	// 퍼센트 (예: 20.00)
	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal rate;

	@Column(name = "started_at", nullable = false)
	private LocalDate startedAt;

	// null이면 현재 유효
	@Column(name = "ended_at")
	private LocalDate endedAt;

	@Builder
	private CommissionRate(String id, String creatorId, String adminId, BigDecimal rate,
			LocalDate startedAt, LocalDate endedAt) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.creatorId = creatorId;
		this.adminId = adminId;
		this.rate = rate;
		this.startedAt = startedAt;
		this.endedAt = endedAt;
	}

	// B-3: 신규 기본 요율 등록 시 겹치는 이전 기본 요율을 자동 마감
	void closeAt(LocalDate date) {
		this.endedAt = date;
	}
}
