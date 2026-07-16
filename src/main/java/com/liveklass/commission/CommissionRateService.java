package com.liveklass.commission;

import com.liveklass.commission.dto.CommissionRateCreateRequest;
import com.liveklass.commission.dto.CommissionRateResponse;
import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import com.liveklass.user.Role;
import com.liveklass.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommissionRateService {

	// 과제 고정값 — 요율 미등록 구간 폴백 (B-3a)
	public static final BigDecimal DEFAULT_RATE = new BigDecimal("20.00");

	private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

	private final CommissionRateRepository commissionRateRepository;
	private final UserRepository userRepository;

	@Transactional
	public CommissionRateResponse create(String adminId, CommissionRateCreateRequest request) {
		if (request.creatorId() != null) {
			var creator = userRepository.findById(request.creatorId())
					.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
							"크리에이터를 찾을 수 없습니다: " + request.creatorId()));
			if (creator.getRole() != Role.CREATOR) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST, "CREATOR가 아닌 사용자입니다.");
			}
		}
		LocalDate end = request.endedAt() != null ? request.endedAt() : MAX_DATE;
		if (end.isBefore(request.startedAt())) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "마감일이 시작일보다 빠를 수 없습니다.");
		}
		if (request.creatorId() == null) {
			// B-3c: 전체 기본 요율 갱신 — 겹치는 이전 기본 요율은 거부 대신 새 요율 시작일 전날로 자동 마감.
			// 이미 시작된(과거) 요율만 자동 마감 대상이며, 아직 시작 전인 미래 요율과 겹치면 모호하므로 그대로 거부한다.
			List<CommissionRate> overlapping = commissionRateRepository
					.findOverlappingDefault(request.startedAt(), end);
			boolean hasFutureConflict = overlapping.stream()
					.anyMatch(r -> !r.getStartedAt().isBefore(request.startedAt()));
			if (hasFutureConflict) {
				throw new BusinessException(ErrorCode.COMMISSION_RATE_PERIOD_OVERLAP);
			}
			overlapping.forEach(r -> r.closeAt(request.startedAt().minusDays(1)));
		} else if (commissionRateRepository.existsOverlap(request.creatorId(), request.startedAt(), end)) {
			// B-3c: 개별 크리에이터 요율은 기간 겹침을 그대로 거부
			throw new BusinessException(ErrorCode.COMMISSION_RATE_PERIOD_OVERLAP);
		}
		CommissionRate rate = CommissionRate.builder()
				.creatorId(request.creatorId())
				.adminId(adminId)
				.rate(request.rate())
				.startedAt(request.startedAt())
				.endedAt(request.endedAt())
				.build();
		return CommissionRateResponse.from(commissionRateRepository.save(rate));
	}

	@Transactional(readOnly = true)
	public List<CommissionRateResponse> list(String creatorId) {
		List<CommissionRate> rates = creatorId != null
				? commissionRateRepository.findByCreatorIdOrderByStartedAtDesc(creatorId)
				: commissionRateRepository.findByCreatorIdIsNullOrderByStartedAtDesc();
		return rates.stream().map(CommissionRateResponse::from).toList();
	}

	/** B-3: 판매 시점 요율 결정 — 개별 크리에이터 > 전체 기본 > 고정 20% 폴백 */
	@Transactional(readOnly = true)
	public BigDecimal resolveRate(String creatorId, LocalDate date) {
		return commissionRateRepository.findApplicable(creatorId, date).stream()
				.max(Comparator.comparing(r -> r.getCreatorId() != null)) // 개별 우선
				.map(CommissionRate::getRate)
				.orElse(DEFAULT_RATE);
	}
}
