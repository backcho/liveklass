package com.liveklass.sale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CancelRecordRepository extends JpaRepository<CancelRecord, String> {

	@Query("select coalesce(sum(cr.refundAmount), 0) from CancelRecord cr where cr.saleRecordId = :saleRecordId")
	long sumRefundBySaleRecordId(@Param("saleRecordId") String saleRecordId);

	// 판매 상세의 취소/환불 이력
	List<CancelRecord> findBySaleRecordIdOrderByCancelledAtAsc(String saleRecordId);

	// 취소는 cancelled_at 기준 월 귀속 (G-3 KST, 월 경계 케이스)
	@Query("""
			select cr from CancelRecord cr, SaleRecord s, Course c
			where s.id = cr.saleRecordId and c.id = s.courseId and c.creatorId = :creatorId
			and cr.cancelledAt >= :from and cr.cancelledAt < :to
			""")
	List<CancelRecord> findByCreatorAndPeriod(@Param("creatorId") String creatorId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	@Query("""
			select cr from CancelRecord cr, SaleRecord s, Course c
			where s.id = cr.saleRecordId and c.id = s.courseId and c.creatorId = :creatorId
			and cr.cancelledAt >= :from and cr.cancelledAt < :to
			""")
	Page<CancelRecord> pageByCreatorAndPeriod(@Param("creatorId") String creatorId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

	@Query("""
			select distinct c.creatorId from CancelRecord cr, SaleRecord s, Course c
			where s.id = cr.saleRecordId and c.id = s.courseId
			and cr.cancelledAt >= :from and cr.cancelledAt < :to
			""")
	List<String> findCreatorIdsWithCancels(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
