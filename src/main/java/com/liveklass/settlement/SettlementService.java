package com.liveklass.settlement;

import com.liveklass.auth.AuthUser;
import com.liveklass.common.dto.PageResponse;
import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import com.liveklass.sale.CancelRecordRepository;
import com.liveklass.sale.SaleRecordRepository;
import com.liveklass.settlement.dto.MonthlySettlementResponse;
import com.liveklass.settlement.dto.SettlementAggregateResponse;
import com.liveklass.settlement.dto.SettlementCreateRequest;
import com.liveklass.settlement.dto.SettlementResponse;
import com.liveklass.settlement.dto.SettlementWithDetailsResponse;
import com.liveklass.user.Role;
import com.liveklass.user.User;
import com.liveklass.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class SettlementService {

	private final SettlementRepository settlementRepository;
	private final SettlementDetailRepository settlementDetailRepository;
	private final SettlementCalculator settlementCalculator;
	private final SaleRecordRepository saleRecordRepository;
	private final CancelRecordRepository cancelRecordRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	/** 크리에이터 월별 정산 — 실시간 집계. CREATOR는 본인만, ADMIN은 전체 (B-4/B-4a) */
	@Transactional(readOnly = true)
	public MonthlySettlementResponse monthly(AuthUser requester, String creatorId, String month) {
		if (requester.getRole() != Role.ADMIN && !requester.getId().equals(creatorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "본인 정산만 조회할 수 있습니다.");
		}
		YearMonth yearMonth = parseMonth(month);
		LocalDateTime from = yearMonth.atDay(1).atStartOfDay();
		LocalDateTime to = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
		return MonthlySettlementResponse.of(creatorId, yearMonth.toString(),
				settlementCalculator.calculate(creatorId, from, to));
	}

	/** 운영자 정산 집계 — 기간 내 크리에이터별 정산 예정 + 전체 합계 */
	@Transactional(readOnly = true)
	public SettlementAggregateResponse aggregate(LocalDate periodStart, LocalDate periodEnd) {
		validatePeriod(periodStart, periodEnd);
		LocalDateTime from = periodStart.atStartOfDay();
		LocalDateTime to = periodEnd.plusDays(1).atStartOfDay();

		// 판매 또는 취소가 있는 크리에이터 전부 (취소만 있는 월은 음수 정산)
		TreeSet<String> creatorIds = new TreeSet<>(saleRecordRepository.findCreatorIdsWithSales(from, to));
		creatorIds.addAll(cancelRecordRepository.findCreatorIdsWithCancels(from, to));

		Map<String, String> names = userRepository.findAllById(creatorIds).stream()
				.collect(java.util.stream.Collectors.toMap(User::getId, User::getName));

		List<SettlementAggregateResponse.CreatorPayout> rows = new ArrayList<>();
		long totalPayout = 0;
		for (String creatorId : creatorIds) {
			var result = settlementCalculator.calculate(creatorId, from, to);
			rows.add(new SettlementAggregateResponse.CreatorPayout(creatorId, names.get(creatorId),
					result.totalSalesAmount(), result.refundAmount(), result.netSalesAmount(),
					result.commissionAmount(), result.payoutAmount(), result.salesCount(),
					result.cancelCount()));
			totalPayout += result.payoutAmount();
		}
		return new SettlementAggregateResponse(periodStart, periodEnd, rows, totalPayout);
	}

	/** B-5/B-6: 정산 확정 생성 — SETTLEMENT + 상세(판매 +, 환불 −) 라인. 기간 겹침 거부 */
	@Transactional
	public SettlementWithDetailsResponse confirmSettlement(String adminId, SettlementCreateRequest request) {
		validatePeriod(request.periodStart(), request.periodEnd());
		userRepository.findById(request.creatorId())
				.filter(u -> u.getRole() == Role.CREATOR)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
						"크리에이터를 찾을 수 없습니다: " + request.creatorId()));
		if (settlementRepository.existsOverlap(request.creatorId(), request.periodStart(), request.periodEnd())) {
			throw new BusinessException(ErrorCode.SETTLEMENT_PERIOD_OVERLAP);
		}

		LocalDateTime from = request.periodStart().atStartOfDay();
		LocalDateTime to = request.periodEnd().plusDays(1).atStartOfDay();
		var result = settlementCalculator.calculate(request.creatorId(), from, to);

		Settlement settlement = settlementRepository.save(Settlement.builder()
				.creatorId(request.creatorId())
				.adminId(adminId)
				.periodStart(request.periodStart())
				.periodEnd(request.periodEnd())
				.totalSalesAmount(result.totalSalesAmount())
				.refundAmount(result.refundAmount())
				.netSalesAmount(result.netSalesAmount())
				.commissionAmount(result.commissionAmount())
				.payoutAmount(result.payoutAmount())
				.build());

		List<SettlementDetail> details = new ArrayList<>();
		result.sales().forEach(s ->
				details.add(SettlementDetail.saleLine(settlement.getId(), s.getId(), s.getAmount())));
		result.cancels().forEach(c ->
				details.add(SettlementDetail.cancelLine(settlement.getId(), c.getId(), c.getRefundAmount())));
		settlementDetailRepository.saveAll(details);

		return SettlementWithDetailsResponse.of(settlement, details);
	}

	@Transactional
	public SettlementResponse changeStatus(String settlementId, SettlementStatus target) {
		Settlement settlement = settlementRepository.findById(settlementId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
						"정산을 찾을 수 없습니다: " + settlementId));
		settlement.changeStatus(target, LocalDateTime.now(clock));
		return SettlementResponse.from(settlement);
	}

	/** 내 정산 목록 — 확정된 과거 이력 (CREATOR) */
	@Transactional(readOnly = true)
	public PageResponse<SettlementResponse> mySettlements(String creatorId, Pageable pageable) {
		return PageResponse.of(settlementRepository.findByCreatorId(creatorId, pageable),
				SettlementResponse::from);
	}

	/** 정산 상세 — 본인(CREATOR) 또는 ADMIN */
	@Transactional(readOnly = true)
	public SettlementWithDetailsResponse detail(AuthUser requester, String settlementId) {
		Settlement settlement = settlementRepository.findById(settlementId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
						"정산을 찾을 수 없습니다: " + settlementId));
		if (requester.getRole() != Role.ADMIN && !requester.getId().equals(settlement.getCreatorId())) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "본인 정산만 조회할 수 있습니다.");
		}
		return SettlementWithDetailsResponse.of(settlement,
				settlementDetailRepository.findBySettlementIdOrderByRecordTypeAscIdAsc(settlementId));
	}

	// B-4a: 형식 오류는 400, 미래 연월은 허용(0원)
	private YearMonth parseMonth(String month) {
		try {
			return YearMonth.parse(month);
		} catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "연월 형식이 올바르지 않습니다 (예: 2025-03): " + month);
		}
	}

	private void validatePeriod(LocalDate start, LocalDate end) {
		if (end.isBefore(start)) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "종료일이 시작일보다 빠를 수 없습니다.");
		}
	}
}
