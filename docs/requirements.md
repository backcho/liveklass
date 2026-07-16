# 요구사항 해석 및 정책 결정

과제 원문 중 해석·결정이 필요한 항목의 기록. 항목 ID(G-\*/A-\*/B-\*/C-\*)로 코드 주석·테스트·다른 문서에서 참조한다. 테이블 설계는 [erd.md](erd.md), 비동기 처리 상세는 [async-design.md](async-design.md).

---

## 공통

### G-1. 과제 범위
공식 요구는 택1이지만 **A(수강신청)/B(정산)/C(알림) 전체 구현**으로 진행한다. 세 도메인이 하나의 이벤트 흐름으로 자연스럽게 이어지기 때문이다([flow-overview.md](flow-overview.md)).

### G-2. 사용자 식별(인증)
Spring Security 기반, USER에 인증 정보(email, password)를 두고 role(ADMIN/CREATOR/STUDENT)로 인가한다. 인증 방식은 **HTTP Basic**을 채택했다 — 구현이 가볍고 Swagger에서 바로 테스트하기 쉽다. JWT는 운영 전환 시 확장 지점.

### G-3. 시간대
JVM·DB·컬럼 전 구간 **Asia/Seoul(KST) 고정**. 월 경계 계산(과제 B) 등 날짜 의존 로직이 서버 환경에 따라 흔들리지 않도록 한다.

### G-4. PK 전략
PK는 **varchar**. 샘플 데이터 id(`creator-1`, `sale-1` 등)를 그대로 시드하기 위한 결정이며, 신규 생성분은 UUID 문자열을 발급한다.

### G-5. 샘플 데이터 주입
앱 기동 시 시드(로컬 프로파일 전용, 멱등)와 별도 등록 API를 병행 제공한다.

---

## 과제 A — 수강 신청

### A-1. 정원 점유 시점 — 결제 확정(CONFIRMED) 시점
근거: "신청 후 결제가 완료되어야 수강 확정됩니다"(과제 시나리오). PENDING은 정원을 점유하지 않는다.
- **신청 시**: 만석(confirmed_count ≥ capacity)이면 대기열 편입(A-6), 아니면 PENDING 생성 — 사전 필터라 락 불필요
- **결제 확정 시**: course 행 X-lock → 정원 재검증(최종 게이트) → confirmed_count 증가 + CONFIRMED 전이. **"마지막 자리 동시 신청" 경합은 결제 확정 API에서 해소한다**
- 강의 상세의 "현재 신청 인원"은 confirmed_count 기준으로 응답한다(참고용으로 PENDING/대기 수 병기 가능)

### A-2. 취소 후 재신청 — 허용
막는 것은 활성(PENDING/CONFIRMED/WAITLISTED) 신청의 중복뿐이다.

#### A-2a. 활성 중복 방지 — generated column + UNIQUE
신청마다 새 행을 생성한다. `active_flag = IF(status IN ('PENDING','CONFIRMED','WAITLISTED'), 1, NULL)`(STORED) + `UNIQUE(course_id, student_id, active_flag)`. MariaDB에 partial index가 없는 것을 우회하는 표준 방식이며, 신청 이력이 자연히 보존된다.

### A-3. 정원 카운트 — 역정규화 카운터 + X-lock
`COURSE.confirmed_count`(수강 확정 인원) 컬럼을 둔다. 결제 확정 트랜잭션에서 `SELECT ... FOR UPDATE`로 course 행 X-lock을 획득한 뒤 검증·증가하고, 취소 시 동일 방식으로 감소한다. A-1에 따라 락이 필요한 지점은 신청이 아니라 **결제 확정·취소**이며, 신청 시점은 무락 조회로 충분하다. 카운터 정합성(= CONFIRMED 행 수)은 동시성 통합 테스트로 보증한다.

### A-4. 취소 기한 — 확정 후 7일
PENDING 취소는 제한이 없다. CONFIRMED 취소는 confirmed_at + 7일 이내(과제 예시 그대로).

### A-5. 강의 상태 전이 — 재오픈·자동 마감 허용
DRAFT→OPEN(수동) / OPEN→CLOSED(수동 + 만석 시 자동) / CLOSED→OPEN(수동 재오픈). 취소로 자리가 나면 대기열 승격이 우선이며, 대기열이 비어 있어도 자동 재오픈은 하지 않는다 — 마감과 달리 재오픈은 크리에이터/운영자의 의사 영역으로 본다.

#### A-5a. 자동 마감과 대기열 편입의 양립
만석 시 자동 CLOSED(A-5)와 "신청은 OPEN만" 규칙이 충돌하면 대기열(A-6) 진입 경로가 사라진다. 그래서 신청 API는 **OPEN이면 정상 흐름, CLOSED라도 만석(confirmed_count ≥ capacity)이면 대기열 편입을 허용**한다. 여석 있는 CLOSED(수동 마감)와 DRAFT는 신청을 거부한다(`COURSE_NOT_OPEN`). 만석 상태에서 수동 마감한 경우에도 대기열 편입은 열려 있는 셈인데, 이는 "취소표 대기" 의미론과 일치하는 의도된 동작이다.

자동 CLOSED 전환은 **결제 확정(A-1) 시점**에만 일어난다(`Course.increaseConfirmed()`). 즉 OPEN인 동안에는 신청(PENDING) 자체는 정원과 무관하게 항상 허용된다 — 같은 강의에 정원보다 많은 PENDING이 동시에 쌓여도 정상이며(신청은 무락, A-1), 정원 초과 여부는 오직 결제 확정 시점에 `COURSE_CAPACITY_EXCEEDED`로만 판정한다. 신청 시점에 대기열(WAITLISTED)로 바로 편입되는 경우는 confirmed_count가 이미 capacity에 도달해 있을 때뿐이다.

#### A-5b. 강의 관리 인가
- **등록**: CREATOR(본인 소유 DRAFT 생성)
- **수정**: 본인 CREATOR 또는 ADMIN(운영자 개입 허용)
- **상태 변경(OPEN/CLOSED/재오픈)**: **ADMIN 전용** — 강사는 불가. 판매 개시·중단은 플랫폼 운영 통제 영역(검수 후 오픈, 운영 사유 마감)이라는 근거다. 만석 자동 마감(A-5)은 시스템 동작이라 그대로 유지한다
- **수강생 목록 조회**: 본인 CREATOR 또는 ADMIN

### A-6. waitlist — 채택
과제의 "대기열(waitlist)"은 **강의별 정원 대기(예약/취소표 대기)**로 해석했다 — 만석 시 순번 등록, 취소 발생 시 순번대로 승격. 접속 폭주 제어용 대기실(waiting room)은 별개 인프라 레이어로 보아 범위 밖으로 뒀다.

- **SOT는 DB**: `ENROLLMENT.status = WAITLISTED`, 순번은 applied_at(동률 대비 id 보조) 순
- 순번 조회는 **DB 인덱스 쿼리**(`INDEX(course_id, status, applied_at)`)로 직접 계산한다(캐시 없음) — "정원 예약 대기열"이라는 해석대로면 트래픽 규모상 인덱스 쿼리로 충분하고, 별도 캐시 레이어는 불필요한 복잡도만 늘린다. 대기 순번 확인 API를 제공한다
- **승격 정책**: CONFIRMED 취소로 자리가 발생하면 대기 1순위를 PENDING으로 전환하고 결제 기한을 부여한다(24시간, `liveklass.waitlist.payment-due-hours` 설정). 기한 초과 시 잡이 자동 취소 후 다음 순번을 승격한다. 승격 시 알림을 발송한다(과제 C 연계). 기한은 `ENROLLMENT.payment_due_at`에 저장하며(승격 건만 non-null), 기한 경과 건의 결제 확정 시도는 거부한다(`PAYMENT_DUE_EXPIRED`)
- 페이지네이션, 크리에이터용 수강생 목록도 채택했다

---

## 과제 B — 정산

### B-1. SaleRecord 생성 경로
결제 확정 시 자동 생성(enrollment_id 연결)하며, 별도 등록 API도 병행 제공한다(샘플 주입·과제 B 필수). 수강 취소 → CANCEL_RECORD도 동일 구조(자동 + API)다.

### B-2. 부분 환불 — 다회 누적
판매 1건에 취소를 N회 허용하고, 누적 환불액이 원 결제액을 넘지 않도록 검증한다.

#### B-2a. 수강 취소의 자동 환불액
CONFIRMED 수강 취소 시 CancelRecord를 자동 생성하며, 환불액은 **원 결제액 − 기환불 누적**(전액 환불 의미론)이다. 잔여가 0이면 생성을 생략한다. 부분 환불은 취소 내역 등록 API 경로로만 발생한다.

### B-3. 수수료율 적용 — 판매 시 스냅샷
SALE_RECORD에 적용 요율을 스냅샷 컬럼으로 남겨 과거 정산의 재현성을 보장한다. 요율 적용은 **결제 확정일(판매 시점) 기준**으로 그 시점에 유효한 요율(started_at ≤ 판매일 ≤ ended_at)을 조회해 결정하며, 요율의 적용 기간 자체에 "월 단위"라는 제약은 없다 — 월 단위는 오직 정산(B) **집계** 주기일 뿐이다. 요율은 개별 크리에이터 요율이 전체 기본 요율보다 우선한다.

#### B-3a. 수수료 계산·반올림
수수료는 **레코드 단위**로 스냅샷 요율을 적용(판매 +, 환불 −)한 뒤 합산한다. 원 미만은 레코드 단위로 HALF_UP 처리한다. 적용 요율이 미등록인 구간은 20%(과제 고정값)로 폴백하며, 시드에 전체 기본 20% 요율(rate-default)을 포함한다. 월 경계 취소로 월 정산이 **음수가 될 수 있는데**, 차감 근거를 보존하기 위해 음수 그대로 응답한다 — 이월 상계는 과제 범위를 넘어서는 별도 운영 정책이라 의도적으로 제외했다.

#### B-3c. 요율 등록의 기간 겹침 처리
- **개별 크리에이터 요율**은 동일 대상·기간이 겹치는 등록을 그대로 **거부**한다(`COMMISSION_RATE_PERIOD_OVERLAP`) — 특정 크리에이터의 요율 변경은 운영자가 기존 이력을 참고해 명시적으로 기간을 나눠 등록해야 하는 신중한 조작으로 본다.
- **전체 기본 요율**(creatorId 미지정)은 겹치는 등록을 거부하지 않고, **겹치는 이전 기본 요율의 종료일을 신규 요율 시작일 전날로 자동 마감한 뒤 신규 요율을 등록**한다. 플랫폼 전체 기본값 갱신은 "지금부터 새 요율로 교체"가 자연스러운 운영 시나리오이기 때문이다. 단, 겹치는 대상에 아직 시작되지 않은(미래) 기본 요율이 있으면 자동 마감 대상을 특정할 수 없으므로 이 경우는 그대로 거부한다.

### B-4. 판매 없는 월 조회 — 0원 정상 응답

#### B-4a. 연월 파라미터 검증
형식 오류(`2025-3`, `2025/03` 등)는 400 INVALID_REQUEST로 응답한다. **미래 연월은 허용**하며 0원으로 응답한다 — 조회 API의 의미론을 유지하기 위함이다.

### B-5. 정산 확정 상태 관리 — 채택
SETTLEMENT.status: PENDING → CONFIRMED → PAID. 정산 확정(생성) API는 운영자 전용이며, 동일 크리에이터·기간이 겹치는 중복 정산은 거부한다. 필수 조회 API(월별 정산)는 계속 실시간 집계로 두고 확정 이력과 분리한다.

### B-6. 정산상세 구성
판매(+정산 대상)와 환불(−차감) 라인을 모두 포함한다. record_type(SALE/CANCEL)으로 구분하고 각각 SALE_RECORD/CANCEL_RECORD를 참조한다. 상세 합계가 정산 총액을 그대로 재현해야 감사가 가능하고, 월 경계 케이스는 환불 라인이 없으면 차감 근거가 남지 않기 때문이다.

---

## 과제 C — 알림

### C-1. 처리 구조 — DB 폴링 + @Scheduled + Spring Batch
요청 API는 테이블 적재 후 즉시 응답하고, `@Scheduled`가 Spring Batch 잡(발송·복구)을 기동한다. 발송부는 인터페이스(`NotificationSender`)로 추상화해 Mock/로그 구현 외에 SQS 등 실브로커로 전환 가능한 구조로 설계했다(과제 요구). 서버 재시작 시 복구는 "상태가 DB에 있음"으로 자연히 충족된다.

### C-2. 상태 모델 — RETRY_WAIT 분리 + DEAD
`PENDING → PROCESSING → SENT | RETRY_WAIT | DEAD`, `RETRY_WAIT → PROCESSING`, `DEAD → PENDING`(수동 재시도, C-7). PROCESSING은 "지금 워커가 잡고 있음"만 의미하고, DEAD는 최종 실패 전용이다.

### C-3. 재시도 정책 — 임계 3회, 5분 고정 간격
대상은 `retry_count < 임계치 AND next_retry_at 경과`다. 수치는 `@ConfigurationProperties`로 설정 관리한다(예: `notification.retry.max-count=3`, `notification.retry.interval=5m`).

### C-4. PROCESSING 고착 복구
처리 타임아웃은 기본 3분이며 설정으로 관리한다 — **재시도 간격(5분)보다 짧게 유지**해 복구와 재시도 폴링이 겹치지 않게 한다. `processing_started_at`을 기록하고, 타임아웃 경과 건은 복구 잡이 RETRY_WAIT로 되돌리며 retry_count를 증가시킨다. 고착 시점에는 직전 발송의 성공 여부를 알 수 없으므로 시스템은 **at-least-once**를 한계로 삼는다 — "중복 발송 방지"는 동일 이벤트의 중복 **요청** 차단(C-6)으로 해석했다.

### C-5. 다중 인스턴스 클레임 — FOR UPDATE SKIP LOCKED
MariaDB 10.6+가 필요하며, 도커 이미지는 10.11 LTS를 쓴다.

### C-6. 중복 발송 판정 키 — (event_id, recipient_id, channel)
UNIQUE 제약이 동시 중복 요청의 최종 방어선이다. event_id는 요청자가 부여하는 멱등키이며, 동일 키 재요청은 에러가 아니라 기존 요청을 그대로 반환한다. 같은 이벤트라도 수신자·채널이 다르면 별건으로 허용한다.

### C-7. 수동 재시도 — 채택
DEAD 상태 대상 운영자 API를 제공한다. retry_count는 **초기화**한다 — 운영자의 수동 개입은 장애 원인 해소 후의 "새 시도"이며, 초기화하지 않으면 1회 실패로 즉시 DEAD가 되어 수동 재시도가 무의미해지기 때문이다.
