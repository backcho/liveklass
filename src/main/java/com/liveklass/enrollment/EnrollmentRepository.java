package com.liveklass.enrollment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, String> {

	// 결제 확정·취소는 신청 행 X-lock으로 동일 신청의 동시 요청을 직렬화
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Enrollment> findWithLockById(String id);

	boolean existsByCourseIdAndStudentIdAndStatusIn(String courseId, String studentId,
			Collection<EnrollmentStatus> statuses);

	long countByCourseIdAndStatus(String courseId, EnrollmentStatus status);

	// A-6: 승격 대상 1순위 — applied_at, id 순. 승격 경합 방지를 위해 X-lock
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Enrollment> findFirstByCourseIdAndStatusOrderByAppliedAtAscIdAsc(String courseId,
			EnrollmentStatus status);

	// A-6: Redis ZSET 유실 시 재구축용 전체 대기열
	List<Enrollment> findByCourseIdAndStatusOrderByAppliedAtAscIdAsc(String courseId, EnrollmentStatus status);

	// A-6: 캐시 불능 시 DB로 순번 계산 (자신 포함 앞선 대기 수 = 1-base 순번)
	@Query("""
			select count(e) from Enrollment e
			where e.courseId = :courseId and e.status = com.liveklass.enrollment.EnrollmentStatus.WAITLISTED
			and (e.appliedAt < :appliedAt or (e.appliedAt = :appliedAt and e.id <= :id))
			""")
	long countWaitlistPosition(@Param("courseId") String courseId, @Param("appliedAt") LocalDateTime appliedAt,
			@Param("id") String id);

	// 승격 결제 기한 만료 건 (A-6)
	List<Enrollment> findByStatusAndPaymentDueAtBefore(EnrollmentStatus status, LocalDateTime now);

	Page<Enrollment> findByStudentId(String studentId, Pageable pageable);

	Page<Enrollment> findByStudentIdAndStatus(String studentId, EnrollmentStatus status, Pageable pageable);

	Page<Enrollment> findByCourseId(String courseId, Pageable pageable);

	Page<Enrollment> findByCourseIdAndStatus(String courseId, EnrollmentStatus status, Pageable pageable);
}
