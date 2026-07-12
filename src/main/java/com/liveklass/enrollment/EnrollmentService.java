package com.liveklass.enrollment;

import com.liveklass.common.dto.PageResponse;
import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import com.liveklass.course.Course;
import com.liveklass.course.CourseRepository;
import com.liveklass.course.CourseStatus;
import com.liveklass.enrollment.dto.EnrollmentResponse;
import com.liveklass.enrollment.dto.WaitlistPositionResponse;
import com.liveklass.enrollment.event.EnrollmentAppliedEvent;
import com.liveklass.enrollment.event.EnrollmentCancelledEvent;
import com.liveklass.enrollment.event.EnrollmentConfirmedEvent;
import com.liveklass.enrollment.event.WaitlistPromotedEvent;
import com.liveklass.user.User;
import com.liveklass.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

	private final EnrollmentRepository enrollmentRepository;
	private final CourseRepository courseRepository;
	private final UserRepository userRepository;
	private final WaitlistCacheService waitlistCacheService;
	private final ApplicationEventPublisher eventPublisher;
	private final EnrollmentProperties enrollmentProperties;
	private final WaitlistProperties waitlistProperties;
	private final Clock clock;

	/**
	 * A-1: 신청은 정원을 점유하지 않으므로 무락. 만석이면 대기열 편입(A-6).
	 * A-5a: OPEN이 아니어도 만석 CLOSED면 대기열 편입 허용.
	 */
	@Transactional
	public EnrollmentResponse apply(String studentId, String courseId) {
		Course course = courseRepository.findById(courseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "강의를 찾을 수 없습니다: " + courseId));
		boolean full = course.isFull();
		if (course.getStatus() == CourseStatus.DRAFT
				|| (course.getStatus() == CourseStatus.CLOSED && !full)) {
			throw new BusinessException(ErrorCode.COURSE_NOT_OPEN);
		}
		if (enrollmentRepository.existsByCourseIdAndStudentIdAndStatusIn(
				courseId, studentId, EnrollmentStatus.ACTIVE_STATUSES)) {
			throw new BusinessException(ErrorCode.DUPLICATE_ACTIVE_ENROLLMENT);
		}

		LocalDateTime now = LocalDateTime.now(clock);
		Enrollment enrollment = full
				? Enrollment.waitlisted(courseId, studentId, now)
				: Enrollment.pending(courseId, studentId, now);
		try {
			// A-2a: 동시 중복 신청의 최종 방어선은 UNIQUE(course_id, student_id, active_flag)
			enrollmentRepository.saveAndFlush(enrollment);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.DUPLICATE_ACTIVE_ENROLLMENT);
		}
		eventPublisher.publishEvent(new EnrollmentAppliedEvent(
				enrollment.getId(), courseId, studentId, enrollment.getStatus(), enrollment.getAppliedAt()));
		return EnrollmentResponse.from(enrollment);
	}

	/**
	 * A-1/A-3: 결제 확정 — course 행 X-lock 후 정원 재검증이 "마지막 자리 동시" 경합의 최종 게이트.
	 * 만석 도달 시 자동 CLOSED(A-5).
	 */
	@Transactional
	public EnrollmentResponse confirm(String studentId, String enrollmentId) {
		Enrollment enrollment = getLockedOwnEnrollment(studentId, enrollmentId);
		if (enrollment.getStatus() != EnrollmentStatus.PENDING) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"결제 확정은 PENDING 상태에서만 가능합니다: " + enrollment.getStatus());
		}
		LocalDateTime now = LocalDateTime.now(clock);
		if (enrollment.isPaymentOverdue(now)) {
			throw new BusinessException(ErrorCode.PAYMENT_DUE_EXPIRED);
		}
		Course course = courseRepository.findWithLockById(enrollment.getCourseId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "강의를 찾을 수 없습니다."));
		course.increaseConfirmed(); // 만석이면 COURSE_CAPACITY_EXCEEDED
		enrollment.confirm(now);
		eventPublisher.publishEvent(new EnrollmentConfirmedEvent(
				enrollment.getId(), enrollment.getCourseId(), studentId));
		return EnrollmentResponse.from(enrollment);
	}

	/**
	 * A-4: PENDING/WAITLISTED 취소 무제한, CONFIRMED는 확정 후 7일 이내.
	 * CONFIRMED 취소 시 X-lock으로 감소 후 대기열 승격(A-6).
	 */
	@Transactional
	public EnrollmentResponse cancel(String studentId, String enrollmentId) {
		Enrollment enrollment = getLockedOwnEnrollment(studentId, enrollmentId);
		EnrollmentStatus previous = enrollment.getStatus();
		LocalDateTime now = LocalDateTime.now(clock);
		switch (previous) {
			case WAITLISTED -> enrollment.cancel(now);
			case PENDING -> {
				boolean promoted = enrollment.isPromoted();
				enrollment.cancel(now);
				if (promoted) {
					// 승격 건 포기 → 비어 있는 자리를 다음 순번에
					promoteNext(enrollment.getCourseId(), now);
				}
			}
			case CONFIRMED -> {
				if (now.isAfter(enrollment.getConfirmedAt().plusDays(enrollmentProperties.cancelPeriodDays()))) {
					throw new BusinessException(ErrorCode.CANCEL_PERIOD_EXPIRED);
				}
				Course course = courseRepository.findWithLockById(enrollment.getCourseId())
						.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "강의를 찾을 수 없습니다."));
				course.decreaseConfirmed();
				enrollment.cancel(now);
				promoteNext(enrollment.getCourseId(), now);
			}
			case CANCELLED -> throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "이미 취소된 신청입니다.");
		}
		eventPublisher.publishEvent(new EnrollmentCancelledEvent(
				enrollment.getId(), enrollment.getCourseId(), studentId, previous));
		return EnrollmentResponse.from(enrollment);
	}

	/** A-6 승격 결제 기한 만료 — 스케줄러가 건별로 호출 (건별 독립 트랜잭션) */
	@Transactional
	public void expirePromotion(String enrollmentId) {
		Enrollment enrollment = enrollmentRepository.findWithLockById(enrollmentId).orElse(null);
		LocalDateTime now = LocalDateTime.now(clock);
		if (enrollment == null || enrollment.getStatus() != EnrollmentStatus.PENDING
				|| !enrollment.isPaymentOverdue(now)) {
			return; // 잡 조회와 처리 사이에 결제·취소된 건 — 스킵
		}
		enrollment.cancel(now);
		eventPublisher.publishEvent(new EnrollmentCancelledEvent(
				enrollment.getId(), enrollment.getCourseId(), enrollment.getStudentId(), EnrollmentStatus.PENDING));
		promoteNext(enrollment.getCourseId(), now);
	}

	@Transactional(readOnly = true)
	public WaitlistPositionResponse waitlistPosition(String studentId, String enrollmentId) {
		Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신청을 찾을 수 없습니다: " + enrollmentId));
		requireOwner(studentId, enrollment);
		if (enrollment.getStatus() != EnrollmentStatus.WAITLISTED) {
			throw new BusinessException(ErrorCode.NOT_WAITLISTED);
		}
		long position = waitlistCacheService.position(enrollment);
		long waitingCount = waitlistCacheService.waitingCount(enrollment.getCourseId());
		return new WaitlistPositionResponse(enrollment.getId(), enrollment.getCourseId(), position, waitingCount);
	}

	@Transactional(readOnly = true)
	public PageResponse<EnrollmentResponse> myEnrollments(String studentId, EnrollmentStatus status,
			Pageable pageable) {
		Page<Enrollment> page = status != null
				? enrollmentRepository.findByStudentIdAndStatus(studentId, status, pageable)
				: enrollmentRepository.findByStudentId(studentId, pageable);
		Map<String, String> titles = courseRepository
				.findAllById(page.getContent().stream().map(Enrollment::getCourseId).distinct().toList())
				.stream().collect(Collectors.toMap(Course::getId, Course::getTitle));
		return PageResponse.of(page, e -> EnrollmentResponse.of(e, titles.get(e.getCourseId()), null));
	}

	/** 크리에이터 전용 — 본인 강의의 수강 신청 목록 */
	@Transactional(readOnly = true)
	public PageResponse<EnrollmentResponse> courseEnrollments(String creatorId, String courseId,
			EnrollmentStatus status, Pageable pageable) {
		Course course = courseRepository.findById(courseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "강의를 찾을 수 없습니다: " + courseId));
		if (!course.getCreatorId().equals(creatorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "본인 강의만 조회할 수 있습니다.");
		}
		Page<Enrollment> page = status != null
				? enrollmentRepository.findByCourseIdAndStatus(courseId, status, pageable)
				: enrollmentRepository.findByCourseId(courseId, pageable);
		Map<String, String> names = userRepository
				.findAllById(page.getContent().stream().map(Enrollment::getStudentId).distinct().toList())
				.stream().collect(Collectors.toMap(User::getId, User::getName));
		return PageResponse.of(page, e -> EnrollmentResponse.of(e, course.getTitle(), names.get(e.getStudentId())));
	}

	// A-6: 대기 1순위(applied_at, id 순)를 PENDING 전환 + 결제 기한 부여. 대기열이 비면 아무 것도 안 함(재오픈은 수동, A-5)
	private void promoteNext(String courseId, LocalDateTime now) {
		enrollmentRepository
				.findFirstByCourseIdAndStatusOrderByAppliedAtAscIdAsc(courseId, EnrollmentStatus.WAITLISTED)
				.ifPresent(next -> {
					next.promote(now.plusHours(waitlistProperties.paymentDueHours()));
					eventPublisher.publishEvent(new WaitlistPromotedEvent(
							next.getId(), courseId, next.getStudentId(), next.getPaymentDueAt()));
				});
	}

	private Enrollment getLockedOwnEnrollment(String studentId, String enrollmentId) {
		Enrollment enrollment = enrollmentRepository.findWithLockById(enrollmentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신청을 찾을 수 없습니다: " + enrollmentId));
		requireOwner(studentId, enrollment);
		return enrollment;
	}

	private void requireOwner(String studentId, Enrollment enrollment) {
		if (!enrollment.getStudentId().equals(studentId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "본인 신청만 처리할 수 있습니다.");
		}
	}

	// 스케줄러용: 기한 만료 승격 건 id 목록
	@Transactional(readOnly = true)
	public List<String> findOverduePromotionIds() {
		return enrollmentRepository
				.findByStatusAndPaymentDueAtBefore(EnrollmentStatus.PENDING, LocalDateTime.now(clock))
				.stream().map(Enrollment::getId).toList();
	}
}
