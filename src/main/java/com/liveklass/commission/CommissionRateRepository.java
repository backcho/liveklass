package com.liveklass.commission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CommissionRateRepository extends JpaRepository<CommissionRate, String> {

	// B-3: 기준일에 유효한 요율 (개별 + 전체 기본 모두 반환, 우선순위는 서비스에서)
	@Query("""
			select r from CommissionRate r
			where (r.creatorId = :creatorId or r.creatorId is null)
			and r.startedAt <= :date and (r.endedAt is null or r.endedAt >= :date)
			""")
	List<CommissionRate> findApplicable(@Param("creatorId") String creatorId, @Param("date") LocalDate date);

	// B-3: 동일 대상(개별 크리에이터 또는 전체 기본)의 기간 겹침 검사. endedAt null은 호출부에서 최대일로 치환
	@Query("""
			select count(r) > 0 from CommissionRate r
			where ((:creatorId is null and r.creatorId is null) or r.creatorId = :creatorId)
			and r.startedAt <= :newEnd and (r.endedAt is null or r.endedAt >= :newStart)
			""")
	boolean existsOverlap(@Param("creatorId") String creatorId,
			@Param("newStart") LocalDate newStart, @Param("newEnd") LocalDate newEnd);

	// B-3c(전체 기본 요율 갱신): 겹치는 기존 기본 요율 조회 — 자동 마감 대상 판정용
	@Query("""
			select r from CommissionRate r
			where r.creatorId is null
			and r.startedAt <= :newEnd and (r.endedAt is null or r.endedAt >= :newStart)
			""")
	List<CommissionRate> findOverlappingDefault(@Param("newStart") LocalDate newStart, @Param("newEnd") LocalDate newEnd);

	List<CommissionRate> findByCreatorIdOrderByStartedAtDesc(String creatorId);

	List<CommissionRate> findByCreatorIdIsNullOrderByStartedAtDesc();
}
