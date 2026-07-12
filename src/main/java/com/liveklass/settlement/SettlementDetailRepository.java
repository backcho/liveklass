package com.liveklass.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementDetailRepository extends JpaRepository<SettlementDetail, String> {

	List<SettlementDetail> findBySettlementIdOrderByRecordTypeAscIdAsc(String settlementId);
}
