package com.liveklass.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liveklass.commission.CommissionRate;
import com.liveklass.commission.CommissionRateRepository;
import com.liveklass.commission.CommissionRateService;
import com.liveklass.course.Course;
import com.liveklass.course.CourseRepository;
import com.liveklass.course.CourseStatus;
import com.liveklass.enrollment.Enrollment;
import com.liveklass.enrollment.EnrollmentRepository;
import com.liveklass.enrollment.EnrollmentStatus;
import com.liveklass.sale.CancelRecord;
import com.liveklass.sale.CancelRecordRepository;
import com.liveklass.sale.SaleRecord;
import com.liveklass.sale.SaleRecordRepository;
import com.liveklass.user.Role;
import com.liveklass.user.User;
import com.liveklass.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * G-5: seed/sample-data.json → 엔티티 주입 (멱등).
 * local 기동(SeedDataLoader)과 과제 B 인수 테스트가 동일 데이터를 공유한다.
 * 시드 사용자 비밀번호 규칙: {id}! (예: creator-1!)
 */
@Component
@RequiredArgsConstructor
public class SeedDataImporter {

	private final UserRepository userRepository;
	private final CourseRepository courseRepository;
	private final CommissionRateRepository commissionRateRepository;
	private final SaleRecordRepository saleRecordRepository;
	private final CancelRecordRepository cancelRecordRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final CommissionRateService commissionRateService;
	private final PasswordEncoder passwordEncoder;
	private final ObjectMapper objectMapper;

	@Transactional
	public void importAll() {
		JsonNode root = readSampleData();

		saveUserIfAbsent("admin-1", "운영자", Role.ADMIN);
		for (JsonNode creator : root.get("creators")) {
			saveUserIfAbsent(creator.get("id").asText(), creator.get("name").asText(), Role.CREATOR);
		}
		for (JsonNode student : root.get("students")) {
			String id = student.asText();
			saveUserIfAbsent(id, "수강생-" + id, Role.STUDENT);
		}

		for (JsonNode course : root.get("courses")) {
			saveCourseIfAbsent(course);
		}
		for (JsonNode rate : root.get("commissionRates")) {
			saveCommissionRateIfAbsent(rate);
		}
		for (JsonNode sale : root.get("saleRecords")) {
			saveSaleIfAbsent(sale);
		}
		for (JsonNode cancel : root.get("cancelRecords")) {
			saveCancelIfAbsent(cancel);
		}
		for (JsonNode enrollment : root.get("enrollments")) {
			saveEnrollmentIfAbsent(enrollment);
		}
	}

	private JsonNode readSampleData() {
		try {
			return objectMapper.readTree(new ClassPathResource("seed/sample-data.json").getInputStream());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void saveUserIfAbsent(String id, String name, Role role) {
		if (userRepository.existsById(id)) {
			return;
		}
		userRepository.save(User.builder()
				.id(id)
				.name(name)
				.email(id + "@liveklass.local")
				.password(passwordEncoder.encode(id + "!"))
				.role(role)
				.build());
	}

	private void saveCourseIfAbsent(JsonNode node) {
		String id = node.get("id").asText();
		if (courseRepository.existsById(id)) {
			return;
		}
		courseRepository.save(Course.builder()
				.id(id)
				.creatorId(node.get("creatorId").asText())
				.title(node.get("title").asText())
				.price(node.get("price").asInt())
				.capacity(node.get("capacity").asInt())
				.status(CourseStatus.valueOf(node.get("status").asText()))
				.build());
	}

	private void saveCommissionRateIfAbsent(JsonNode node) {
		String id = node.get("id").asText();
		if (commissionRateRepository.existsById(id)) {
			return;
		}
		commissionRateRepository.save(CommissionRate.builder()
				.id(id)
				.creatorId(node.hasNonNull("creatorId") ? node.get("creatorId").asText() : null)
				.adminId("admin-1")
				.rate(node.get("rate").decimalValue())
				.startedAt(LocalDate.parse(node.get("startedAt").asText()))
				.endedAt(node.hasNonNull("endedAt") ? LocalDate.parse(node.get("endedAt").asText()) : null)
				.build());
	}

	private void saveSaleIfAbsent(JsonNode node) {
		String id = node.get("id").asText();
		if (saleRecordRepository.existsById(id)) {
			return;
		}
		String courseId = node.get("courseId").asText();
		LocalDateTime paidAt = parseKst(node.get("paidAt").asText());
		Course course = courseRepository.findById(courseId).orElseThrow();
		saleRecordRepository.save(SaleRecord.builder()
				.id(id)
				.courseId(courseId)
				.studentId(node.get("studentId").asText())
				.amount(node.get("amount").asInt())
				.commissionRate(commissionRateService.resolveRate(course.getCreatorId(), paidAt.toLocalDate()))
				.paidAt(paidAt)
				.build());
	}

	private void saveCancelIfAbsent(JsonNode node) {
		String id = node.get("id").asText();
		if (cancelRecordRepository.existsById(id)) {
			return;
		}
		cancelRecordRepository.save(CancelRecord.builder()
				.id(id)
				.saleRecordId(node.get("saleRecordId").asText())
				.refundAmount(node.get("refundAmount").asInt())
				.cancelledAt(parseKst(node.get("cancelledAt").asText()))
				.build());
	}

	// 샘플의 +09:00 오프셋 표기 → KST LocalDateTime (G-3)
	private LocalDateTime parseKst(String value) {
		return OffsetDateTime.parse(value).toLocalDateTime();
	}

	// 대기열 시연용(course-4/5) 신청 데이터. id가 없으므로 (course, student) 활성 신청 존재 여부로 멱등 판정
	private void saveEnrollmentIfAbsent(JsonNode node) {
		String courseId = node.get("courseId").asText();
		String studentId = node.get("studentId").asText();
		if (enrollmentRepository.existsByCourseIdAndStudentIdAndStatusIn(
				courseId, studentId, EnrollmentStatus.ACTIVE_STATUSES)) {
			return;
		}
		EnrollmentStatus status = EnrollmentStatus.valueOf(node.get("status").asText());
		LocalDateTime appliedAt = parseKst(node.get("appliedAt").asText());
		Enrollment enrollment = status == EnrollmentStatus.WAITLISTED
				? Enrollment.waitlisted(courseId, studentId, appliedAt)
				: Enrollment.pending(courseId, studentId, appliedAt);
		if (status == EnrollmentStatus.CONFIRMED) {
			// A-3/A-5: 확정 인원 반영 — 정원 도달 시 course가 자동으로 CLOSED 전환
			Course course = courseRepository.findById(courseId).orElseThrow();
			course.increaseConfirmed();
			enrollment.confirm(parseKst(node.get("confirmedAt").asText()));
		}
		enrollmentRepository.save(enrollment);
	}
}
