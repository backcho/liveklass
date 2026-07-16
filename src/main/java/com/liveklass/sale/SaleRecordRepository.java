package com.liveklass.sale;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRecordRepository extends JpaRepository<SaleRecord, String> {

	// B-2: 누적 환불 검증은 판매 행 X-lock 안에서 (동시 부분 환불 직렬화)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<SaleRecord> findWithLockById(String id);

	Optional<SaleRecord> findByEnrollmentId(String enrollmentId);

	// 크리에이터는 course 경유 (ERD) — [from, to) 반개구간
	@Query("""
			select s from SaleRecord s, Course c
			where c.id = s.courseId and c.creatorId = :creatorId
			and s.paidAt >= :from and s.paidAt < :to
			""")
	List<SaleRecord> findByCreatorAndPeriod(@Param("creatorId") String creatorId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	@Query("""
			select s from SaleRecord s, Course c
			where c.id = s.courseId and c.creatorId = :creatorId
			and s.paidAt >= :from and s.paidAt < :to
			""")
	Page<SaleRecord> pageByCreatorAndPeriod(@Param("creatorId") String creatorId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

	// 운영자 판매 관리 목록 — 전체 크리에이터, [from, to) 반개구간
	@Query("select s from SaleRecord s where s.paidAt >= :from and s.paidAt < :to")
	Page<SaleRecord> pageByPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
			Pageable pageable);

	// 운영자 집계: 기간 내 판매가 있는 크리에이터
	@Query("""
			select distinct c.creatorId from SaleRecord s, Course c
			where c.id = s.courseId and s.paidAt >= :from and s.paidAt < :to
			""")
	List<String> findCreatorIdsWithSales(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
