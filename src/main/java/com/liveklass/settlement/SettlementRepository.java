package com.liveklass.settlement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface SettlementRepository extends JpaRepository<Settlement, String> {

	// B-5: 동일 크리에이터 기간 겹침 거부
	@Query("""
			select count(s) > 0 from Settlement s
			where s.creatorId = :creatorId and s.periodStart <= :end and s.periodEnd >= :start
			""")
	boolean existsOverlap(@Param("creatorId") String creatorId,
			@Param("start") LocalDate start, @Param("end") LocalDate end);

	Page<Settlement> findByCreatorId(String creatorId, Pageable pageable);
}
