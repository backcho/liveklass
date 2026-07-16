# LiveKlass 백엔드 과제 — 수강신청 · 정산 · 알림

**대표 과제는 C(알림 발송 시스템)** 입니다. 공식 요구는 택1이지만, 세 과제가 도메인 이벤트로 자연스럽게 이어지는 구조라 **A(수강신청)·B(정산)·C(알림) 전체를 구현**했습니다 — 결제 확정이 판매(B)를 만들고, 신청·확정·취소·대기열 승격이 알림(C)을 만듭니다. 선택 구현 항목도 전부 포함했습니다(대기열, 취소 기한, 수강생 목록, 페이지네이션 / 정산 확정 상태·중복 방지·수수료율 이력 / 수동 재시도).

## 1. 실행 방법

요구 사항: **Docker**(MariaDB 10.11 컨테이너), **Java 21**(`.sdkmanrc` 포함 — `sdk env` 사용 가능)

```bash
docker compose up -d                                        # MariaDB(호스트 13306)
./gradlew bootRun --args='--spring.profiles.active=local'   # 기동 시 샘플 데이터 자동 시드(멱등)
```

- Swagger: **http://localhost:8080/swagger-ui.html** → 우측 상단 Authorize(HTTP Basic)
- 테스트: `./gradlew test` — **통합 테스트 68개**, Testcontainers가 MariaDB를 별도로 띄우므로 Docker만 있으면 됨
- 데이터 초기화: `docker compose down -v && docker compose up -d` 후 재기동 → 시드부터 다시 시작

### 시드 계정 (비밀번호 규칙: `{id}!`)

| 계정 | 이메일 / 비밀번호 | 역할 |
|---|---|---|
| admin-1 | admin-1@liveklass.local / `admin-1!` | 운영자 |
| creator-1~3 | creator-1@liveklass.local / `creator-1!` | 크리에이터 |
| student-1~7 | student-1@liveklass.local / `student-1!` | 수강생 |

## 2. 기술 스택

| 구분 | 선택 | 비고 |
|---|---|---|
| 프레임워크 | Spring Boot 3.5 / Java 21 | Spring MVC + Data JPA + Security + Batch |
| DB | MariaDB 10.11 LTS | `FOR UPDATE SKIP LOCKED`(10.6+) 사용, 대기열 순번도 인덱스 쿼리로 DB에서 직접 계산 |
| 비동기 | `@Scheduled` + Spring Batch | DB 폴링 발송 잡 — 브로커 없이, 브로커 전환 가능 구조 |
| 인증 | Spring Security HTTP Basic | role(ADMIN/CREATOR/STUDENT) 인가, 실패 응답도 공통 에러 규격 |
| 문서/테스트 | springdoc-openapi, Testcontainers | 테스트는 전부 실제 MariaDB 대상 통합 테스트 |

전 구간 **KST 고정**(JVM·DB 세션·직렬화), PK는 **varchar** — 샘플 데이터 id(`creator-1`, `sale-1`)를 그대로 시드하기 위한 결정입니다.

## 3. 설계 결정 요약

모든 정책 결정은 **[docs/requirements.md](docs/requirements.md)** 에 항목 ID(G-\*/A-\*/B-\*/C-\*)와 근거로 기록했고, 코드 주석·테스트가 이 ID를 참조합니다. 테이블 설계는 [docs/erd.md](docs/erd.md).

| | 결정 | 근거 |
|---|---|---|
| A-1 | 정원 점유는 **결제 확정 시점**. 신청(PENDING)은 무락 | "결제가 완료되어야 수강 확정" — 마지막 자리 경합은 확정 API에서 course 행 X-lock으로 해소 |
| A-2a | 활성 신청 중복은 **generated column + UNIQUE** | MariaDB partial index 부재 우회. 동시 중복 신청의 최종 방어선을 DB 제약으로 |
| A-6 | 대기열 SOT는 DB, 순번 조회는 인덱스 쿼리(캐시 없음) | 취소 시 1순위 자동 승격 + 24h 결제 기한, 만료 시 잡이 다음 순번 승격. 최초엔 Redis ZSET 캐시를 뒀으나 대기실(waiting room)과 혼동했던 것이라 판단해 제거(2026-07-16) |
| B-3/B-3a | 수수료율 **판매 시점 스냅샷**, 레코드 단위 계산(HALF_UP) | 월 단위 "순판매×20%"는 요율 변경 이력과 양립 불가 — 균일 20%면 과제 기대값과 동일 |
| B-6 | 정산 상세는 판매(+)/환불(−) 라인 모두 보존 | 상세 합계 = 순판매 재현(감사), 월 경계 취소의 차감 근거 보존 — 월 정산 음수 허용 |
| C-1 | **DB 적재(outbox) + 폴링 발송** | 적재가 비즈니스 트랜잭션에 참여해 유실 창 없음. 발송부는 `NotificationSender` 인터페이스 — SQS 전환 지점 |
| C-5 | 다중 인스턴스 클레임은 `FOR UPDATE SKIP LOCKED` | 두 워커가 같은 알림을 잡을 수 없음 — 동시성 테스트로 증명 |
| C-6 | 중복 발송 방지 키 = `(event_id, recipient_id, channel)` UNIQUE | 동일 키 재요청은 에러가 아니라 기존 요청 반환(멱등) |

**과제 C 필수 제출물** — 비동기 처리 구조·상태 모델·재시도 정책·운영 시나리오(고착 복구/재시작/다중 인스턴스)·실브로커 전환 방안: **[docs/async-design.md](docs/async-design.md)**

## 4. 검증

- 통합 테스트 **68개** (`./gradlew test`) — 원문 "샘플 데이터 검증 시나리오" 4건은 `SettlementAcceptanceTest`에 인수 테스트로 고정
- 클린 체크아웃에서 `docker compose up` → 기동 → E2E(강의 개설→신청→확정→판매→알림→취소→환불→대기열 승격→월별 정산) 31개 체크포인트 실기동 재현 확인

### 추가 검증 케이스와 추가 이유 (샘플 데이터 밖)

| 케이스 | 추가 이유 | 테스트 |
|---|---|---|
| 잔여 1석에 10스레드 동시 결제 확정 | 과제 A의 핵심 요구 "마지막 자리 동시 신청" — 정확히 1명 확정·나머지 409·자동 마감까지 검증 | `EnrollmentConcurrencyTest` |
| 동일 사용자 5스레드 동시 신청 | 사전 조회만으로는 못 막는 동시 중복 — UNIQUE 제약이 막는지 | `EnrollmentConcurrencyTest` |
| 승격 후 24h 미결제 → 자동 취소·다음 순번 승격 | 대기열 승격이 "자리 무한 점유"가 되지 않는지 (Clock 조작으로 재현) | `WaitlistFlowTest` |
| 취소 기한(7일) 경과 후 취소 시도 | 과제 예시 정책의 경계 검증 | `EnrollmentFlowTest` |
| 부분 환불 다회 누적, 원금 초과 거부, 잔여 정확히 소진 | 환불 누적 한도의 경계값 — 동시 환불은 판매 행 X-lock 직렬화 | `SaleCommissionRuleTest` |
| 요율 변경 전후 판매의 수수료 차이(20%↔10%) | 스냅샷이 과거 정산을 보존하는지 (가산점 항목의 실검증) | `SaleCommissionRuleTest` |
| 월 경계: 1월 판매 +48,000 / 2월 취소 −48,000 | 취소만 있는 월의 **음수 정산** 허용 검증 | `SettlementAcceptanceTest` |
| 잘못된 연월 형식 400 / **미래 연월은 0원 정상 응답** | 원문 "추가 데이터 가이드"가 예시한 케이스 — 조회 API 의미론 유지 | `SettlementAcceptanceTest` |
| 같은 멱등키 5스레드 동시 알림 접수 → 1건 생성·전원 동일 응답 | 과제 C "동시에 같은 요청" — 에러가 아닌 멱등 수렴인지 | `NotificationConcurrencyTest` |
| 2개 워커 동시 폴링 → 클레임 교집합 0·무유실 | 다중 인스턴스 요구를 실제 경합으로 증명 | `NotificationConcurrencyTest` |
| 실패 → 5분 후 재시도 → 3회 도달 DEAD → 수동 재시도(카운트 초기화) | 재시도 정책 전체 수명주기 (실패 주입 Sender) | `NotificationRetryFlowTest` |
| PROCESSING 고착 3분 경과 회수, **반복 고착도 임계 도달 시 DEAD** | 복구가 무한 재시도 루프가 되지 않는지 | `NotificationRecoveryTest` |
| 재기동 시나리오: 잔존 PENDING/RETRY_WAIT를 다음 폴링이 처리 | "서버 재시작 유실 없음" 요구의 직접 검증 | `NotificationRetryFlowTest` |
| 미인증 401·권한 부족 403도 공통 에러 규격 | Security 실패 응답이 규격을 깨지 않는지 | `SecurityErrorFormatTest` |

## 5. 요구사항 해석 및 개선 의견

### 해석

- **대기열(waitlist)** — "강의별 정원 대기(취소표 대기)"로 해석했습니다: 만석 시 순번 등록, 취소 발생 시 순번대로 승격. 접속 폭주 제어용 대기실(waiting room)은 별개 인프라 레이어로 판단해 범위 밖으로 두었습니다.
- **만석 자동 마감 vs 대기열 편입 충돌(A-5a)** — 만석 시 자동 CLOSED로 하면 "신청은 OPEN만" 규칙과 대기열 진입이 충돌합니다. **CLOSED라도 만석이면 대기열 편입을 허용**하고, 여석 있는 수동 CLOSED·DRAFT만 신청을 거부하는 것으로 해소했습니다.
- **"중복 발송 금지"와 at-least-once** — 고착 복구 시점에는 직전 발송의 성공 여부를 알 수 없으므로(발송 후 기록 전 다운) 전달 의미론은 **at-least-once**가 한계입니다. exactly-once는 발송 채널의 멱등성 없이는 불가능하므로, 과제의 "중복 발송 금지"는 **동일 이벤트의 중복 '요청' 차단**(UNIQUE 멱등키, C-6)으로 해석했습니다. 채널 멱등키 전달은 실브로커 전환 시 확장 지점입니다.
- **알림 실패가 비즈니스에 영향 금지 + 예외 무시 금지** — 적재는 비즈니스 트랜잭션에 참여(원자적)하고, 발송 실패는 비즈니스와 무관한 잡에서 발생하며 무시가 아니라 **상태(RETRY_WAIT/DEAD)와 failure_reason으로 기록**되어 재시도됩니다.
- **수동 재시도의 카운트 초기화(과제가 명시적으로 묻는 항목)** — **초기화**로 정책화했습니다. 운영자의 수동 개입은 장애 원인 해소 후의 "새 시도"이며, 초기화하지 않으면 1회 실패로 즉시 DEAD가 되어 수동 재시도가 무의미해집니다.
- **여러 기기의 동시 읽음 처리** — `is_read = true` 단방향 멱등 연산이라 순서와 무관하게 동일 상태로 수렴하므로 락 없이 처리했습니다.

### 개선 의견 (과제 원문에 대해)

- **수수료 정의와 요율 이력의 충돌** — 필수 구현의 "수수료 = 순 판매의 20%"(월 단위)와 선택 구현의 "수수료율 변경 이력"은 양립하지 않습니다(월 중간 요율 변경 시 월 단위 계산 불가). 레코드 단위 "판매 시점 요율 적용 후 합산"으로 정의를 바꾸는 것을 제안합니다 — 균일 20%면 결과가 같아 기대값도 유지됩니다.
- **샘플 데이터에 취소 내역 부재** — 시나리오 표는 cancel-1~3을 전제하는데 JSON에는 saleRecords만 있습니다. 표의 기대값(환불 110,000 = 80k+30k, 월 경계 60k)에서 역산해 시드를 구성했습니다. 취소 JSON도 원문에 포함되면 구현 간 비교가 쉬워질 것입니다.
- **월 경계 취소의 음수 정산** — sale-5/cancel-3 케이스에서 "취소만 있는 월"의 정산이 음수가 됩니다. 음수 허용(차감 근거 보존)으로 구현했지만, 이월 상계·미수금 처리 등 기대 동작이 원문에 명시되면 해석 편차가 줄 것입니다.

## 6. 한계와 확장 지점

- 인증은 HTTP Basic(과제 시연 최적화) — JWT 전환 여지. 스키마는 `ddl-auto`(과제 규모 고려) — 운영 전환 시 Flyway
- 발송 예약·타입별 템플릿(선택 미구현)은 확장 지점만 설계에 반영: 예약은 클레임 조건의 "기한 도래"와 동일 패턴으로 자연 확장
- 정산 확정은 생성 시점 스냅샷 — 확정 후 소급 취소는 다음 정산에서 조정하는 운영 정책 필요(범위 밖)
