package com.liveklass.enrollment;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "enrollments",
		uniqueConstraints = @UniqueConstraint(name = "uk_enrollment_active",
				columnNames = {"course_id", "student_id", "active_flag"}),
		indexes = @Index(name = "idx_enrollment_waitlist", columnList = "course_id, status, applied_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment extends BaseTimeEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "course_id", nullable = false, length = 36)
	private String courseId;

	@Column(name = "student_id", nullable = false, length = 36)
	private String studentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EnrollmentStatus status;

	// A-2a: MariaDB partial index 우회 — 활성이면 1, 아니면 NULL(유니크 미충돌). DB가 계산·저장
	@Column(name = "active_flag", insertable = false, updatable = false,
			columnDefinition = "tinyint GENERATED ALWAYS AS (IF(status IN ('PENDING','CONFIRMED','WAITLISTED'), 1, NULL)) STORED")
	private Integer activeFlag;

	// A-6: 대기열 순번 기준 (동률은 id 보조)
	@Column(name = "applied_at", nullable = false)
	private LocalDateTime appliedAt;

	private LocalDateTime confirmedAt;

	private LocalDateTime cancelledAt;

	// A-6 승격 정책: 대기열 승격 건만 non-null — 이 기한 내 결제 확정 필요
	private LocalDateTime paymentDueAt;

	private Enrollment(String courseId, String studentId, EnrollmentStatus status, LocalDateTime appliedAt) {
		this.id = UUID.randomUUID().toString();
		this.courseId = courseId;
		this.studentId = studentId;
		this.status = status;
		this.appliedAt = appliedAt;
	}

	public static Enrollment pending(String courseId, String studentId, LocalDateTime appliedAt) {
		return new Enrollment(courseId, studentId, EnrollmentStatus.PENDING, appliedAt);
	}

	public static Enrollment waitlisted(String courseId, String studentId, LocalDateTime appliedAt) {
		return new Enrollment(courseId, studentId, EnrollmentStatus.WAITLISTED, appliedAt);
	}

	public void confirm(LocalDateTime now) {
		requireStatus(EnrollmentStatus.PENDING);
		this.status = EnrollmentStatus.CONFIRMED;
		this.confirmedAt = now;
	}

	public void cancel(LocalDateTime now) {
		if (status == EnrollmentStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "이미 취소된 신청입니다.");
		}
		this.status = EnrollmentStatus.CANCELLED;
		this.cancelledAt = now;
	}

	// A-6: 대기열 승격 — PENDING 전환 + 결제 기한 부여
	public void promote(LocalDateTime paymentDueAt) {
		requireStatus(EnrollmentStatus.WAITLISTED);
		this.status = EnrollmentStatus.PENDING;
		this.paymentDueAt = paymentDueAt;
	}

	public boolean isPromoted() {
		return paymentDueAt != null;
	}

	public boolean isPaymentOverdue(LocalDateTime now) {
		return paymentDueAt != null && now.isAfter(paymentDueAt);
	}

	private void requireStatus(EnrollmentStatus expected) {
		if (status != expected) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"허용되지 않는 상태 전이입니다: " + status + " (요구 상태: " + expected + ")");
		}
	}
}
