# ERD

[requirements.md](requirements.md) 결정 반영본. 전 구간 KST(G-3), PK는 varchar(G-4, 샘플 데이터 호환).

```mermaid
erDiagram
  USER ||--o{ COURSE : "creator"
  USER ||--o{ ENROLLMENT : "student"
  COURSE ||--o{ ENROLLMENT : "has"
  ENROLLMENT |o--o| SALE_RECORD : "auto-created"
  COURSE ||--o{ SALE_RECORD : "has"
  USER ||--o{ SALE_RECORD : "student"
  SALE_RECORD ||--o{ CANCEL_RECORD : "refunded-by"
  USER |o--o{ COMMISSION_RATE : "creator-or-default"
  USER ||--o{ SETTLEMENT : "creator"
  SETTLEMENT ||--o{ SETTLEMENT_DETAIL : "has"
  SALE_RECORD |o--o{ SETTLEMENT_DETAIL : "sale-line"
  CANCEL_RECORD |o--o{ SETTLEMENT_DETAIL : "cancel-line"
  USER ||--o{ NOTIFICATION_REQUEST : "recipient"

  USER {
    varchar id PK
    varchar name
    varchar email UK "Spring Security 인증 (G-2)"
    varchar password "해시 저장"
    varchar role "ADMIN | CREATOR | STUDENT"
    datetime created_at
  }
  COURSE {
    varchar id PK
    varchar creator_id FK
    varchar title
    varchar description
    int price
    int capacity "수강 정원"
    int confirmed_count "확정 인원 역정규화 카운터 (A-3)"
    varchar status "DRAFT | OPEN | CLOSED, 만석 시 자동 CLOSED (A-5)"
    datetime start_date
    datetime end_date
    datetime created_at
    datetime updated_at
  }
  ENROLLMENT {
    varchar id PK
    varchar course_id FK
    varchar student_id FK
    varchar status "PENDING | CONFIRMED | CANCELLED | WAITLISTED"
    tinyint active_flag "generated column, 활성이면 1 아니면 NULL (A-2a)"
    datetime applied_at "대기열 순번 기준 (A-6)"
    datetime confirmed_at
    datetime cancelled_at
    datetime payment_due_at "승격 건 결제 기한, 승격 건만 non-null (A-6)"
  }
  SALE_RECORD {
    varchar id PK
    varchar enrollment_id FK "nullable, API 직접 등록분은 없음 (B-1)"
    varchar course_id FK
    varchar student_id FK
    int amount
    decimal commission_rate "판매 시점 요율 스냅샷 (B-3)"
    datetime paid_at
  }
  CANCEL_RECORD {
    varchar id PK
    varchar sale_record_id FK
    int refund_amount "누적 합이 원 결제액 이하 (B-2)"
    datetime cancelled_at
  }
  COMMISSION_RATE {
    varchar id PK
    varchar creator_id FK "nullable, null이면 전체 기본 요율"
    varchar admin_id FK "등록자"
    decimal rate "퍼센트, 0.01 - 99.99"
    date started_at
    date ended_at "nullable, null이면 현재 유효"
    datetime created_at
  }
  SETTLEMENT {
    varchar id PK
    varchar creator_id FK
    varchar admin_id FK "정산 담당자"
    date period_start
    date period_end
    int total_sales_amount "총 판매"
    int refund_amount "환불 차감"
    int net_sales_amount "순 판매"
    int commission_amount "수수료"
    int payout_amount "정산 예정 금액"
    varchar status "PENDING | CONFIRMED | PAID (B-5)"
    datetime confirmed_at
    datetime paid_at
    datetime created_at
  }
  SETTLEMENT_DETAIL {
    varchar id PK
    varchar settlement_id FK
    varchar record_type "SALE | CANCEL (B-6)"
    varchar sale_record_id FK "record_type=SALE일 때"
    varchar cancel_record_id FK "record_type=CANCEL일 때"
    int amount "SALE은 양수, CANCEL은 음수"
  }
  NOTIFICATION_REQUEST {
    varchar id PK
    varchar recipient_id FK
    varchar event_id "요청자 부여 멱등키 (C-6)"
    varchar type
    varchar channel "EMAIL | IN_APP"
    varchar reference_id
    varchar status "PENDING | PROCESSING | SENT | RETRY_WAIT | DEAD (C-2)"
    boolean is_read
    int retry_count
    datetime next_retry_at
    datetime processing_started_at "고착 감지 기준 (C-4)"
    varchar failure_reason
    datetime created_at
    datetime updated_at
  }
```

## 제약·인덱스 (다이어그램으로 표현 못 하는 것)

| 테이블 | 제약/인덱스 | 근거 |
|---|---|---|
| ENROLLMENT | `UNIQUE(course_id, student_id, active_flag)` — active_flag는 STORED generated column `IF(status IN ('PENDING','CONFIRMED','WAITLISTED'), 1, NULL)` | A-2a 활성 중복 방지 |
| ENROLLMENT | `INDEX(course_id, status, applied_at)` — 대기열 순번·승격 조회 | A-6 |
| NOTIFICATION_REQUEST | `UNIQUE(event_id, recipient_id, channel)` | C-6 중복 발송 방지 |
| NOTIFICATION_REQUEST | `INDEX(status, next_retry_at)` — 폴링 클레임 | C-5 |
| SALE_RECORD | `INDEX(course_id, paid_at)` — 정산 집계(creator는 course 경유) | B 집계 |
| CANCEL_RECORD | `INDEX(cancelled_at)` — 취소 월 기준 집계 | B 집계 |
| COMMISSION_RATE | 개별 크리에이터는 동일 대상 기간 겹침 등록 거부. 전체 기본 요율(creatorId null)은 거부 대신 겹치는 이전 요율을 자동 마감 후 교체 — 앱 검증 | B-3c |
| SETTLEMENT | 동일 creator 기간 겹침 금지 — 확정 생성 시 앱 검증 | B-5 |
| CANCEL_RECORD | 누적 refund_amount ≤ 원 SALE_RECORD.amount — 앱 검증 | B-2 |

## 애플리케이션 규칙 메모

- `COURSE.confirmed_count` 증감은 course 행 X-lock(`SELECT ... FOR UPDATE`) 트랜잭션 안에서만 수행 — 락의 필수 지점은 결제 확정·취소 (A-1/A-3)
- 대기열 순번의 SOT는 DB(ENROLLMENT WAITLISTED + applied_at). 순번 조회는 `INDEX(course_id, status, applied_at)` 기반 쿼리로 직접 계산하며, 별도 캐시 레이어는 두지 않는다 (A-6)
- 알림 클레임은 `FOR UPDATE SKIP LOCKED` — MariaDB 10.6+ 필요, docker 이미지 10.11 LTS 권장 (C-5)
- 재시도 임계·간격·처리 타임아웃은 `@ConfigurationProperties`로 외부화 (C-3/C-4)
