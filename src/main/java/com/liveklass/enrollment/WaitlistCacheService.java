package com.liveklass.enrollment;

import com.liveklass.config.TimeZoneConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A-6: 대기 순번 조회 캐시. SOT는 DB(WAITLISTED + applied_at, id 순) — Redis ZSET은
 * 순번 조회 전용이며 유실·미스 시 DB에서 재구축한다. score = applied_at epoch millis,
 * 동점(밀리초 절사)은 ZSET의 member 사전순 정렬이 DB의 id 보조 정렬과 일치.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitlistCacheService {

	private final StringRedisTemplate redisTemplate;
	private final EnrollmentRepository enrollmentRepository;

	private String key(String courseId) {
		return "waitlist:course:" + courseId;
	}

	public void add(String courseId, String enrollmentId, LocalDateTime appliedAt) {
		redisTemplate.opsForZSet().add(key(courseId), enrollmentId, score(appliedAt));
	}

	public void remove(String courseId, String enrollmentId) {
		redisTemplate.opsForZSet().remove(key(courseId), enrollmentId);
	}

	/** 1-base 순번. 캐시 미스면 DB에서 ZSET 재구축, Redis 불능이면 DB 카운트로 폴백 */
	public long position(Enrollment enrollment) {
		try {
			Long rank = redisTemplate.opsForZSet().rank(key(enrollment.getCourseId()), enrollment.getId());
			if (rank == null) {
				rebuild(enrollment.getCourseId());
				rank = redisTemplate.opsForZSet().rank(key(enrollment.getCourseId()), enrollment.getId());
			}
			if (rank != null) {
				return rank + 1;
			}
		} catch (Exception e) {
			log.warn("waitlist 캐시 조회 실패 — DB 폴백: courseId={}", enrollment.getCourseId(), e);
		}
		return enrollmentRepository.countWaitlistPosition(
				enrollment.getCourseId(), enrollment.getAppliedAt(), enrollment.getId());
	}

	public long waitingCount(String courseId) {
		try {
			Long size = redisTemplate.opsForZSet().zCard(key(courseId));
			if (size != null && size > 0) {
				return size;
			}
		} catch (Exception e) {
			log.warn("waitlist 캐시 카운트 실패 — DB 폴백: courseId={}", courseId, e);
		}
		return enrollmentRepository.countByCourseIdAndStatus(courseId, EnrollmentStatus.WAITLISTED);
	}

	public void rebuild(String courseId) {
		List<Enrollment> waitlist = enrollmentRepository
				.findByCourseIdAndStatusOrderByAppliedAtAscIdAsc(courseId, EnrollmentStatus.WAITLISTED);
		redisTemplate.delete(key(courseId));
		if (waitlist.isEmpty()) {
			return;
		}
		Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
		for (Enrollment enrollment : waitlist) {
			tuples.add(ZSetOperations.TypedTuple.of(enrollment.getId(), score(enrollment.getAppliedAt())));
		}
		redisTemplate.opsForZSet().add(key(courseId), tuples);
	}

	private double score(LocalDateTime appliedAt) {
		return appliedAt.atZone(TimeZoneConfig.KST).toInstant().toEpochMilli();
	}
}
