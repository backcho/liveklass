package com.liveklass.course;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, String> {

	// A-3: SELECT ... FOR UPDATE — 결제 확정·취소 트랜잭션의 정원 검증·증감용
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Course> findWithLockById(String id);

	Page<Course> findByStatus(CourseStatus status, Pageable pageable);
}
