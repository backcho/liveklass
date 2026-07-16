# 코드 컨벤션·구조 스니펫

> 목적: 코드 탐색 없이 이 문서만 읽고 기존 스타일에 맞는 코드를 쓸 수 있게 한다. 새 패턴이 생기면 이 문서를 갱신한다.

## 패키지 구조

```
com.liveklass
├── config/                  # TimeZoneConfig, JpaConfig, SecurityConfig, OpenApiConfig (+ 필요 시 추가)
├── common/
│   ├── entity/BaseTimeEntity    # createdAt/updatedAt 감사 (@CreatedDate/@LastModifiedDate)
│   └── error/                   # ErrorCode, ErrorResponse, BusinessException, GlobalExceptionHandler
├── auth/                    # AuthUser(UserDetails), AuthUserDetailsService
├── seed/SeedDataLoader      # @Profile("local") ApplicationRunner, seed/sample-data.json 로드
└── {domain}/                # user, course, enrollment, sale, settlement, notification ...
    ├── {Entity}.java, {Enum}.java, {Entity}Repository.java, {Domain}Service.java  # 도메인 루트에 평면 배치
    ├── api/{Domain}Controller.java
    └── dto/{...}Request.java, {...}Response.java   # record + @Schema
```

- 들여쓰기: **탭**. 한 클래스 한 파일. import는 와일드카드 없이 명시.
- 주석: 정책 근거는 requirements 항목 ID로 표기 — 예: `// G-4: 샘플 데이터 id 호환을 위한 varchar PK`
- 테스트 메서드명은 한국어: `void 미인증_요청은_401과_공통_에러_규격으로_응답한다()`

## 엔티티 스니펫 (User 패턴)

```java
@Entity
@Table(name = "users")               // 예약어 회피 등 필요 시만 name 지정
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	// G-4: varchar PK, 미지정 시 UUID 발급
	@Id
	@Column(length = 36)
	private String id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Builder
	private User(String id, ...) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		...
	}
}
```

- 연관관계는 FK id 컬럼(String)으로 보관(`creatorId` 등) — 객체 참조 매핑은 필요한 곳만
- setter 금지. 상태 변경은 의도가 드러나는 도메인 메서드로 (`confirm(now)`, `cancel(now)` 등)
- 시간 타입은 `LocalDateTime` (전 구간 KST 고정이라 zone 불필요, G-3)

## 에러 처리

```java
throw new BusinessException(ErrorCode.COURSE_CAPACITY_EXCEEDED);          // 기본 메시지
throw new BusinessException(ErrorCode.NOT_FOUND, "강의를 찾을 수 없습니다: " + id); // 상세 메시지
```

- 새 에러는 `ErrorCode` enum에 추가 (HttpStatus + 한국어 메시지). 과제별 섹션 주석 아래 배치
- 상태 충돌류는 `HttpStatus.CONFLICT`
- 컨트롤러/서비스에서 직접 ResponseEntity로 에러 만들지 않기 — 전부 예외로 던지고 GlobalExceptionHandler가 공통 규격(ErrorResponse) 변환

## 컨트롤러·DTO

```java
@Tag(name = "Course", description = "강의 관리")
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

	@Operation(summary = "강의 등록")
	@PostMapping
	public CourseResponse create(@AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody CourseCreateRequest request) { ... }
}
```

- 응답 DTO는 record + `@Schema(description/example)`, 정적 팩토리 `from(entity)`
- 요청 DTO는 record + Bean Validation(`@NotBlank`, `@Positive` 등)
- 현재 사용자: `@AuthenticationPrincipal AuthUser` (id/name/email/role 보유)
- 역할 인가: `@PreAuthorize("hasRole('CREATOR')")` (@EnableMethodSecurity 활성) 또는 SecurityConfig 경로 규칙(`/api/admin/**`)
- 소유권 검증은 서비스에서 `ErrorCode.FORBIDDEN`으로

## 서비스

```java
@Service
@RequiredArgsConstructor
public class CourseService {
	private final CourseRepository courseRepository;

	@Transactional            // 조회 전용은 @Transactional(readOnly = true)
	public CourseResponse create(...) { ... }
}
```

- 현재 시각은 `Clock` 빈 주입 후 `LocalDateTime.now(clock)` — 테스트에서 시각 조작 가능하게

## 테스트

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)   // mariadb:10.11, @ServiceConnection
@ActiveProfiles("test")                      // ddl-auto: create-drop
class SomethingTest { ... }
```

- 인증: `mockMvc.perform(get("/...").with(httpBasic(email, password)))` (spring-security-test)
- 데이터는 각 테스트가 직접 준비(시드는 local 전용이라 test에 없음)

## 시드

- `seed/sample-data.json` → `SeedDataLoader`(local 전용). 멱등: `existsById` 확인 후 저장
- 시드 비밀번호 규칙 `{id}!`. 이메일 `{id}@liveklass.local`
- 새 엔티티 시드는 SeedDataLoader의 TODO 위치에 추가

## 도메인 이벤트

- 도메인 간 연결점은 `{domain}/event/` record + `ApplicationEventPublisher.publishEvent(...)` — 서비스 트랜잭션 안에서 발행
- 구독은 동기 `@EventListener` — 원 트랜잭션에 참여해 원자성 보장(SaleRecord 자동 생성, 알림 적재 모두 outbox 성격)

## 동시성

- 카운터 증감·정원 검증은 `@Lock(PESSIMISTIC_WRITE) Optional<T> findWithLockById(String id)` 트랜잭션 안에서만
- 락 순서 고정: **enrollment → course** (교착 방지)
- DB 제약이 최종 방어선인 경우 `saveAndFlush` + `catch (DataIntegrityViolationException)` → BusinessException 변환

## 테스트 시각 조작

```java
@SpringBootTest
@Import(IntegrationTestConfiguration.class)  // Testcontainers + @Primary MutableClock
@ActiveProfiles("test")
class SomeTest {
	@Autowired MutableClock clock;           // clock.reset()/set(LocalDateTime)/plus(Duration)
}
```

- 스케줄러 잡은 test 프로파일에서 비활성(`liveklass.scheduling.enabled=false`) — 잡 메서드 직접 호출

## 배치·폴링 잡

- @Scheduled(트리거) → Spring Batch Job(Tasklet) → 서비스 호출 구조. 스텝은 `ResourcelessTransactionManager`(무트랜잭션) — 트랜잭션은 서비스의 REQUIRES_NEW로만
- 클레임 쿼리: `@Lock(PESSIMISTIC_WRITE)` + `@QueryHint(name="jakarta.persistence.lock.timeout", value="-2")` = FOR UPDATE SKIP LOCKED
- 외부 IO(발송 등)는 반드시 트랜잭션 밖에서

## 멱등 접수 패턴

- 사전 조회 → saveAndFlush → `catch (DataIntegrityViolationException)` 시 기존 행 재조회 반환 (에러 아님)
- 테스트 발송기 실패 주입: `support/ToggleableNotificationSender` (@Primary, IntegrationTestConfiguration)

## API 관례

- 경로: `/api/{복수 리소스}` + 하위 리소스 중첩(`/api/courses/{id}/enrollments`), 행위성 전이는 POST(`/confirm`, `/cancel`, `/status`)
- 페이지네이션: `page`(0-base)/`size` 쿼리 파라미터 → 공통 `PageResponse<T>` 래퍼
- 설정값 외부화: `@ConfigurationProperties` (예: `liveklass.waitlist.*`, `notification.retry.*`)
