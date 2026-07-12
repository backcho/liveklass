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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sale_records",
		indexes = @Index(name = "idx_sale_course_paid", columnList = "course_id, paid_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleRecord extends BaseTimeEntity {

	// G-4: 샘플 데이터 id(sale-1 등) 호환
	@Id
	@Column(length = 36)
	private String id;

	// B-1: 결제 확정 자동 생성분만 연결, API 직접 등록분은 null
	@Column(name = "enrollment_id", length = 36)
	private String enrollmentId;

	@Column(name = "course_id", nullable = false, length = 36)
	private String courseId;

	@Column(name = "student_id", nullable = false, length = 36)
	private String studentId;

	@Column(nullable = false)
	private int amount;

	// B-3: 판매 시점 요율 스냅샷 (퍼센트) — 과거 정산 재현성
	@Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
	private BigDecimal commissionRate;

	@Column(name = "paid_at", nullable = false)
	private LocalDateTime paidAt;

	@Builder
	private SaleRecord(String id, String enrollmentId, String courseId, String studentId, int amount,
			BigDecimal commissionRate, LocalDateTime paidAt) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.enrollmentId = enrollmentId;
		this.courseId = courseId;
		this.studentId = studentId;
		this.amount = amount;
		this.commissionRate = commissionRate;
		this.paidAt = paidAt;
	}

	// B-3a: 레코드 단위 수수료 — 원 미만 HALF_UP
	public long commissionOf(long baseAmount) {
		return BigDecimal.valueOf(baseAmount)
				.multiply(commissionRate)
				.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
				.longValueExact();
	}
}
