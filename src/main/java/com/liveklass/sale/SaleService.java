package com.liveklass.sale;

import com.liveklass.commission.CommissionRateService;
import com.liveklass.common.dto.PageResponse;
import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import com.liveklass.course.Course;
import com.liveklass.course.CourseRepository;
import com.liveklass.sale.dto.CancelCreateRequest;
import com.liveklass.sale.dto.CancelRecordResponse;
import com.liveklass.sale.dto.SaleCreateRequest;
import com.liveklass.sale.dto.SaleRecordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleService {

	private static final LocalDateTime MIN = LocalDateTime.of(1970, 1, 1, 0, 0);
	private static final LocalDateTime MAX = LocalDateTime.of(9999, 12, 31, 0, 0);

	private final SaleRecordRepository saleRecordRepository;
	private final CancelRecordRepository cancelRecordRepository;
	private final CourseRepository courseRepository;
	private final CommissionRateService commissionRateService;

	/** B-1: 판매 내역 등록 API 경로 (운영자 — 샘플 주입·독립 사용) */
	@Transactional
	public SaleRecordResponse create(SaleCreateRequest request) {
		Course course = getCourse(request.courseId());
		if (request.id() != null && saleRecordRepository.existsById(request.id())) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 존재하는 판매 내역입니다: " + request.id());
		}
		SaleRecord sale = SaleRecord.builder()
				.id(request.id())
				.courseId(course.getId())
				.studentId(request.studentId())
				.amount(request.amount())
				.commissionRate(commissionRateService.resolveRate(course.getCreatorId(),
						request.paidAt().toLocalDate())) // B-3 스냅샷
				.paidAt(request.paidAt())
				.build();
		return SaleRecordResponse.of(saleRecordRepository.save(sale), course.getTitle());
	}

	/** B-1: 결제 확정 자동 생성 경로 — 확정 트랜잭션에 참여 */
	@Transactional
	public SaleRecord createFromEnrollment(String enrollmentId, String courseId, String studentId,
			LocalDateTime paidAt) {
		Course course = getCourse(courseId);
		SaleRecord sale = SaleRecord.builder()
				.enrollmentId(enrollmentId)
				.courseId(courseId)
				.studentId(studentId)
				.amount(course.getPrice())
				.commissionRate(commissionRateService.resolveRate(course.getCreatorId(), paidAt.toLocalDate()))
				.paidAt(paidAt)
				.build();
		return saleRecordRepository.save(sale);
	}

	/** B-2: 취소(환불) 내역 등록 — 판매 행 X-lock으로 누적 검증 직렬화 */
	@Transactional
	public CancelRecordResponse registerCancel(String saleRecordId, CancelCreateRequest request) {
		if (request.id() != null && cancelRecordRepository.existsById(request.id())) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 존재하는 취소 내역입니다: " + request.id());
		}
		CancelRecord cancel = createCancel(saleRecordId, request.id(), request.refundAmount(),
				request.cancelledAt());
		return CancelRecordResponse.from(cancel);
	}

	/** B-2a: 수강 취소 자동 환불 — 잔여 전액. 잔여 0이면 생성 생략 */
	@Transactional
	public void refundRemainingForEnrollment(String enrollmentId, LocalDateTime cancelledAt) {
		saleRecordRepository.findByEnrollmentId(enrollmentId).ifPresent(sale -> {
			long remaining = sale.getAmount() - cancelRecordRepository.sumRefundBySaleRecordId(sale.getId());
			if (remaining > 0) {
				createCancel(sale.getId(), null, (int) remaining, cancelledAt);
			}
		});
	}

	private CancelRecord createCancel(String saleRecordId, String id, int refundAmount,
			LocalDateTime cancelledAt) {
		SaleRecord sale = saleRecordRepository.findWithLockById(saleRecordId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
						"판매 내역을 찾을 수 없습니다: " + saleRecordId));
		long refunded = cancelRecordRepository.sumRefundBySaleRecordId(saleRecordId);
		if (refunded + refundAmount > sale.getAmount()) {
			throw new BusinessException(ErrorCode.REFUND_EXCEEDS_SALE_AMOUNT);
		}
		return cancelRecordRepository.save(CancelRecord.builder()
				.id(id)
				.saleRecordId(saleRecordId)
				.refundAmount(refundAmount)
				.cancelledAt(cancelledAt)
				.build());
	}

	/** 내 강의 판매 목록 (CREATOR, 기간 필터) */
	@Transactional(readOnly = true)
	public PageResponse<SaleRecordResponse> mySales(String creatorId, LocalDateTime from, LocalDateTime to,
			Pageable pageable) {
		Page<SaleRecord> page = saleRecordRepository.pageByCreatorAndPeriod(creatorId,
				from != null ? from : MIN, to != null ? to : MAX, pageable);
		Map<String, String> titles = courseRepository
				.findAllById(page.getContent().stream().map(SaleRecord::getCourseId).distinct().toList())
				.stream().collect(Collectors.toMap(Course::getId, Course::getTitle));
		return PageResponse.of(page, s -> SaleRecordResponse.of(s, titles.get(s.getCourseId())));
	}

	/** 내 강의 환불 목록 (CREATOR, 기간 필터) */
	@Transactional(readOnly = true)
	public PageResponse<CancelRecordResponse> myCancels(String creatorId, LocalDateTime from, LocalDateTime to,
			Pageable pageable) {
		Page<CancelRecord> page = cancelRecordRepository.pageByCreatorAndPeriod(creatorId,
				from != null ? from : MIN, to != null ? to : MAX, pageable);
		return PageResponse.of(page, CancelRecordResponse::from);
	}

	private Course getCourse(String courseId) {
		return courseRepository.findById(courseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "강의를 찾을 수 없습니다: " + courseId));
	}
}
