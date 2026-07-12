package com.liveklass.sale.dto;

import com.liveklass.sale.SaleRecord;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "판매 내역")
public record SaleRecordResponse(
		@Schema(example = "sale-1") String id,
		@Schema(description = "결제 확정 자동 생성분만 연결 (B-1)") String enrollmentId,
		@Schema(example = "course-1") String courseId,
		@Schema(example = "Spring Boot 입문") String courseTitle,
		@Schema(example = "student-1") String studentId,
		@Schema(example = "50000") int amount,
		@Schema(description = "판매 시점 요율 스냅샷(%) (B-3)", example = "20.00") BigDecimal commissionRate,
		LocalDateTime paidAt
) {

	public static SaleRecordResponse of(SaleRecord sale, String courseTitle) {
		return new SaleRecordResponse(sale.getId(), sale.getEnrollmentId(), sale.getCourseId(), courseTitle,
				sale.getStudentId(), sale.getAmount(), sale.getCommissionRate(), sale.getPaidAt());
	}
}
