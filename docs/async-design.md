# 알림 비동기 처리 구조 및 재시도 정책 (과제 C 필수 제출물)

관련 정책 결정: [requirements.md](requirements.md) C-1 ~ C-7. 구현: `com.liveklass.notification`.

## 1. 전체 구조 — DB 폴링 + @Scheduled + Spring Batch (C-1)

```
[비즈니스 API / 알림 요청 API]
        │  ① 적재: NOTIFICATION_REQUEST INSERT (원 트랜잭션에 참여, outbox 방식)
        ▼
┌─────────────────────┐   ② @Scheduled(10s) → notificationSendJob (Spring Batch)
│ notification_requests │──③ 클레임 tx: FOR UPDATE SKIP LOCKED (C-5)
│  (상태 = SOT, DB)     │      PENDING + 기한 도래 RETRY_WAIT → PROCESSING 전이 후 커밋
└─────────────────────┘   ④ 발송: NotificationSender.send() — 트랜잭션 밖 외부 IO
        ▲                 ⑤ 결과 tx: SENT / 실패 시 RETRY_WAIT·DEAD (C-2/C-3)
        │
        └─ @Scheduled(60s) → notificationRecoveryJob: PROCESSING 고착 회수 (C-4)
```

- **요청 API는 적재 후 즉시 응답**(202). 발송은 폴링 잡이 비동기로 수행 — API 스레드와 완전 분리
- **적재는 원 트랜잭션에 참여**(동기 @EventListener): 비즈니스 커밋 = 알림 요청 커밋 (원자적, outbox 패턴). 유실 창이 없다
- **"알림 실패가 비즈니스에 영향 금지 + 예외 무시 금지" 해석**: 발송 실패는 비즈니스 트랜잭션과 무관한 잡에서 발생하며, 무시되는 게 아니라 **상태(RETRY_WAIT/DEAD)와 failure_reason으로 기록**되고 재시도된다
- 트랜잭션 경계: 클레임/결과 기록은 짧은 독립 트랜잭션(REQUIRES_NEW), 발송(외부 IO)은 트랜잭션 밖 — DB 커넥션·락을 외부 지연에 노출하지 않음. Batch 스텝 자체는 ResourcelessTransactionManager(무트랜잭션 래핑)

## 2. 상태 모델 (C-2)

```
            ┌────────────────────────────────┐
            ▼                                │ (수동 재시도, ADMIN, C-7)
PENDING ──클레임──▶ PROCESSING ──성공──▶ SENT │
   ▲                   │                     │
   │                   ├─실패(임계 미만)─▶ RETRY_WAIT ──기한 도래, 클레임──▶ PROCESSING
   │                   └─실패(임계 도달)─▶ DEAD ─────────────────────────────┘
   └── (RETRY_WAIT도 클레임 시 PROCESSING)
```

- `PROCESSING`은 "지금 워커가 잡고 있음"만 의미 — 종료 상태가 아니며 고착 시 복구 대상
- `DEAD`는 최종 실패 전용. 자동 경로로는 벗어나지 않고 수동 재시도로만 PENDING 복귀

## 3. 재시도 정책 (C-3)

| 항목 | 값 | 설정 키 |
|---|---|---|
| 실패 임계 (총 시도 횟수) | 3회 | `liveklass.notification.retry.max-count` |
| 재시도 간격 | 5분 고정 | `liveklass.notification.retry.interval` |
| 처리 타임아웃 (고착 판정) | 3분 | `liveklass.notification.processing-timeout` |
| 클레임 배치 크기 | 100 | `liveklass.notification.batch-size` |

- 실패 시 `retry_count++`, `next_retry_at = now + interval`, `failure_reason` 기록. `retry_count ≥ max-count`면 DEAD
- 재시도 대상 선별: `status = RETRY_WAIT AND next_retry_at ≤ now` (PENDING은 즉시 대상)
- **타임아웃(3분) < 재시도 간격(5분)** 유지 — 복구로 RETRY_WAIT 복귀한 건이 다음 재시도 폴링과 겹치지 않게

## 4. 운영 시나리오 대응

### 4-1. PROCESSING 고착 복구 (C-4)
워커 다운·응답 유실로 PROCESSING에 머문 건은 `processing_started_at + 3분` 경과 시 복구 잡이 `retry_count++`와 함께 RETRY_WAIT로 회수한다(임계 도달 시 DEAD). 테스트: `NotificationRecoveryTest`.

### 4-2. 서버 재시작 유실 없음
상태의 SOT가 DB이므로 별도 복구 절차가 없다 — 재기동 후 첫 폴링이 잔존 PENDING/RETRY_WAIT를 그대로 클레임한다. 테스트: `NotificationRetryFlowTest.재기동_시나리오…`.

### 4-3. 다중 인스턴스 중복 처리 방지 (C-5)
클레임 쿼리가 `FOR UPDATE SKIP LOCKED`(MariaDB 10.6+)로 행을 선점 — 다른 인스턴스는 잠긴 행을 건너뛰므로 같은 알림을 두 워커가 잡을 수 없다. 클레임 즉시 PROCESSING으로 전이·커밋해 소유권을 영속화한다. 테스트: `NotificationConcurrencyTest.두_워커가_동시에_폴링해도…`.

### 4-4. 중복 발송 방지 (C-6) — at-least-once 해석
- **동일 이벤트 중복 "요청" 차단**: `UNIQUE(event_id, recipient_id, channel)`이 동시 요청의 최종 방어선. 동일 키 재요청은 에러가 아니라 기존 요청 반환. 이벤트 연동분의 event_id는 `이벤트종류:enrollmentId` 결정적 키
- **전달 의미론은 at-least-once**: 고착 복구 시점엔 직전 발송의 성공 여부를 알 수 없으므로(발송 후 SENT 기록 전 다운) 재시도가 중복 전달이 될 수 있다. exactly-once는 발송 채널의 멱등성 없이는 불가능하며, 과제의 "중복 발송 금지"는 요청 단위 중복 차단(C-6)으로 해석 — 채널 멱등키 전달은 실브로커 전환 시 확장 지점

## 5. 실브로커(SQS 등) 전환 방안

발송부는 `NotificationSender` 인터페이스 하나로 추상화되어 있다 (현재 구현: `LogNotificationSender` — Mock/로그).

- **최소 전환**: `SqsNotificationSender implements NotificationSender`로 교체 — send()가 SQS 발행. 폴링·재시도·상태 관리는 그대로 (SQS가 "전송 채널"인 구성)
- **완전 전환**: 적재 테이블을 outbox로 유지하고 발송 잡을 "outbox → 브로커 릴레이"로 축소, 소비자(컨슈머)가 실제 발송·재시도를 담당. 상태 모델의 SENT를 "브로커 인계 완료"로 재정의. DLQ가 DEAD를 대체
- 어느 쪽이든 **요청 API·적재 경로·중복 방지 키는 변경 없음**

## 6. 정책 판단 기록 (과제가 명시적으로 묻는 항목)

- **수동 재시도 시 retry_count 초기화** (C-7): 운영자의 수동 개입은 장애 원인 해소 후의 "새 시도"로 간주 → 초기화. 초기화하지 않으면 1회 실패 즉시 다시 DEAD가 되어 수동 재시도의 의미가 없음
- **읽음 처리 동시성**: `is_read = true` 단방향 멱등 연산 — 여러 기기의 동시 읽음 요청은 순서와 무관하게 동일 상태로 수렴하므로 락 불필요
- **미구현 선택 항목**: 발송 예약(특정 시각), 타입별 템플릿 — 확장 지점만 언급: 예약은 `next_retry_at`과 동일한 "기한 도래" 클레임 조건으로 자연 확장 가능
