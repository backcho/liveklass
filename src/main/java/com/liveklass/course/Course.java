package com.liveklass.course;

import com.liveklass.common.entity.BaseTimeEntity;
import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseTimeEntity {

	// G-4: 샘플 데이터 id(course-1 등) 호환을 위한 varchar PK
	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "creator_id", nullable = false, length = 36)
	private String creatorId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 1000)
	private String description;

	@Column(nullable = false)
	private int price;

	@Column(nullable = false)
	private int capacity;

	// A-3: 확정 인원 역정규화 카운터. 증감은 course 행 X-lock 트랜잭션 안에서만
	@Column(nullable = false)
	private int confirmedCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CourseStatus status;

	private LocalDateTime startDate;

	private LocalDateTime endDate;

	@Builder
	private Course(String id, String creatorId, String title, String description, int price, int capacity,
			CourseStatus status, LocalDateTime startDate, LocalDateTime endDate) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.creatorId = creatorId;
		this.title = title;
		this.description = description;
		this.price = price;
		this.capacity = capacity;
		this.confirmedCount = 0;
		this.status = status != null ? status : CourseStatus.DRAFT;
		this.startDate = startDate;
		this.endDate = endDate;
	}

	public void update(String title, String description, int price, int capacity,
			LocalDateTime startDate, LocalDateTime endDate) {
		if (capacity < confirmedCount) {
			throw new BusinessException(ErrorCode.CAPACITY_LESS_THAN_CONFIRMED);
		}
		this.title = title;
		this.description = description;
		this.price = price;
		this.capacity = capacity;
		this.startDate = startDate;
		this.endDate = endDate;
	}

	public void changeStatus(CourseStatus target) {
		if (!status.canTransitionTo(target)) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"허용되지 않는 상태 전이입니다: " + status + " → " + target);
		}
		this.status = target;
	}

	public boolean isFull() {
		return confirmedCount >= capacity;
	}

	// A-1/A-3: 결제 확정의 최종 게이트 — 호출 전 course 행 X-lock 필수
	public void increaseConfirmed() {
		if (isFull()) {
			throw new BusinessException(ErrorCode.COURSE_CAPACITY_EXCEEDED);
		}
		this.confirmedCount++;
		if (isFull() && status == CourseStatus.OPEN) {
			this.status = CourseStatus.CLOSED; // A-5: 만석 시 자동 마감
		}
	}

	// 취소로 자리가 나도 자동 재오픈은 하지 않는다 — 대기열 승격 우선, 재오픈은 수동 (A-5 부속)
	public void decreaseConfirmed() {
		this.confirmedCount--;
	}
}
