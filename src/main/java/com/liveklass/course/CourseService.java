package com.liveklass.course;

import com.liveklass.auth.AuthUser;
import com.liveklass.common.dto.PageResponse;
import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import com.liveklass.course.dto.CourseCreateRequest;
import com.liveklass.course.dto.CourseDetailResponse;
import com.liveklass.course.dto.CourseResponse;
import com.liveklass.course.dto.CourseUpdateRequest;
import com.liveklass.enrollment.EnrollmentRepository;
import com.liveklass.enrollment.EnrollmentStatus;
import com.liveklass.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseService {

	private final CourseRepository courseRepository;
	private final EnrollmentRepository enrollmentRepository;

	@Transactional
	public CourseResponse create(String creatorId, CourseCreateRequest request) {
		validatePeriod(request.startDate(), request.endDate());
		Course course = Course.builder()
				.creatorId(creatorId)
				.title(request.title())
				.description(request.description())
				.price(request.price())
				.capacity(request.capacity())
				.startDate(request.startDate())
				.endDate(request.endDate())
				.build();
		return CourseResponse.from(courseRepository.save(course));
	}

	/** A-5b: 수정은 본인 CREATOR 또는 ADMIN */
	@Transactional
	public CourseResponse update(AuthUser actor, String courseId, CourseUpdateRequest request) {
		validatePeriod(request.startDate(), request.endDate());
		Course course = getManagedCourse(actor, courseId);
		course.update(request.title(), request.description(), request.price(), request.capacity(),
				request.startDate(), request.endDate());
		return CourseResponse.from(course);
	}

	/** A-5b: 상태 변경은 ADMIN 전용 — 역할 게이트는 컨트롤러(@PreAuthorize). 만석 자동 마감은 별도(시스템) */
	@Transactional
	public CourseResponse changeStatus(String courseId, CourseStatus target) {
		Course course = getCourse(courseId);
		course.changeStatus(target);
		return CourseResponse.from(course);
	}

	@Transactional(readOnly = true)
	public PageResponse<CourseResponse> list(CourseStatus status, Pageable pageable) {
		var page = status != null
				? courseRepository.findByStatus(status, pageable)
				: courseRepository.findAll(pageable);
		return PageResponse.of(page, CourseResponse::from);
	}

	@Transactional(readOnly = true)
	public CourseDetailResponse detail(String courseId) {
		Course course = getCourse(courseId);
		long pending = enrollmentRepository.countByCourseIdAndStatus(courseId, EnrollmentStatus.PENDING);
		long waitlisted = enrollmentRepository.countByCourseIdAndStatus(courseId, EnrollmentStatus.WAITLISTED);
		return CourseDetailResponse.of(course, pending, waitlisted);
	}

	private Course getCourse(String courseId) {
		return courseRepository.findById(courseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "강의를 찾을 수 없습니다: " + courseId));
	}

	// A-5b: 본인 CREATOR 또는 ADMIN
	private Course getManagedCourse(AuthUser actor, String courseId) {
		Course course = getCourse(courseId);
		if (actor.getRole() != Role.ADMIN && !course.getCreatorId().equals(actor.getId())) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "본인 강의만 관리할 수 있습니다.");
		}
		return course;
	}

	private void validatePeriod(java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
		if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "종료일이 시작일보다 빠를 수 없습니다.");
		}
	}
}
