package com.liveklass.settlement;

import com.liveklass.sale.CancelRecord;
import com.liveklass.sale.CancelRecordRepository;
import com.liveklass.sale.SaleRecord;
import com.liveklass.sale.SaleRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 실시간 정산 집계 (B-5: 필수 조회는 실시간, 확정 이력과 분리).
 * 판매는 paid_at, 취소는 cancelled_at 기준 [from, to) 귀속 (G-3 KST).
 * 수수료는 레코드 단위 스냅샷 요율 — 환불 라인은 원 판매의 요율로 차감 (B-3/B-3a).
 */
@Component
@RequiredArgsConstructor
public class SettlementCalculator {

	private final SaleRecordRepository saleRecordRepository;
	private final CancelRecordRepository cancelRecordRepository;

	public record Result(List<SaleRecord> sales, List<CancelRecord> cancels,
			long totalSalesAmount, long refundAmount, long netSalesAmount,
			long commissionAmount, long payoutAmount) {

		public int salesCount() {
			return sales.size();
		}

		public int cancelCount() {
			return cancels.size();
		}
	}

	public Result calculate(String creatorId, LocalDateTime from, LocalDateTime to) {
		List<SaleRecord> sales = saleRecordRepository.findByCreatorAndPeriod(creatorId, from, to);
		List<CancelRecord> cancels = cancelRecordRepository.findByCreatorAndPeriod(creatorId, from, to);

		// 환불 라인의 요율은 원 판매의 스냅샷 (기간 밖 판매의 취소 — 월 경계 케이스 포함)
		Map<String, SaleRecord> salesOfCancels = saleRecordRepository
				.findAllById(cancels.stream().map(CancelRecord::getSaleRecordId).distinct().toList())
				.stream().collect(Collectors.toMap(SaleRecord::getId, Function.identity()));

		long totalSales = sales.stream().mapToLong(SaleRecord::getAmount).sum();
		long refund = cancels.stream().mapToLong(CancelRecord::getRefundAmount).sum();
		long commission = sales.stream().mapToLong(s -> s.commissionOf(s.getAmount())).sum()
				- cancels.stream().mapToLong(c ->
						salesOfCancels.get(c.getSaleRecordId()).commissionOf(c.getRefundAmount())).sum();
		long net = totalSales - refund;
		long payout = net - commission;

		return new Result(sales, cancels, totalSales, refund, net, commission, payout);
	}
}
