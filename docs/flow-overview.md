# 전체 기능 플로우 (A → B → C 연결)

A(수강신청)·B(정산)·C(알림)는 별도 기능이 아니라 하나의 이벤트 흐름이다: **결제 확정이 판매(B)를 만들고, 신청·확정·취소·대기열 승격이 알림(C)을 만든다.** 이 문서는 그 연결만 보여준다. 각 도메인 내부 상세는 다음을 참조:

- 테이블 구조: [erd.md](erd.md)
- 알림 상태 모델·재시도 정책: [async-design.md](async-design.md)
- 정책 근거(항목 ID): [requirements.md](requirements.md)

```mermaid
flowchart TD
    subgraph A["A. 수강신청"]
        A1[크리에이터: 강의 개설\nDRAFT]
        A2[강의 공개\nOPEN]
        A3{여석 있음?}
        A4[학생 신청\nENROLLMENT: PENDING]
        A5[대기열 등록\nWAITLISTED]
        A6[결제 확정\nCONFIRMED\ncourse X-lock, confirmed_count++]
        A7{정원 도달?}
        A8[COURSE 자동 CLOSED]
        A9[학생 취소\n확정 후 7일 이내]
        A10[ENROLLMENT: CANCELLED]
        A11{대기 순번 존재?}
        A12[1순위 승격\nWAITLISTED → PENDING\npayment_due_at = +24h]
        A13{24h 내 결제?}
        A14[배치: 자동취소 후\n다음 순번 승격]

        A1 --> A2 --> A3
        A3 -->|Yes| A4
        A3 -->|No, 만석| A5
        A4 --> A6
        A6 --> A7
        A7 -->|Yes| A8
        A6 --> A9 --> A10
        A10 --> A11
        A11 -->|Yes| A12
        A12 -->|No| A14 --> A12
        A12 -->|Yes 결제완료| A6
    end

    subgraph B["B. 정산"]
        B1[SALE_RECORD 생성\n요율 스냅샷, HALF_UP]
        B2[CANCEL_RECORD 생성\n환불액 ≤ 원금 검증]
        B3[월별 집계\nSETTLEMENT: PENDING]
        B4[정산 확정\nCONFIRMED]
        B5[지급 완료\nPAID]

        B1 --> B3
        B2 --> B3
        B3 --> B4 --> B5
    end

    subgraph C["C. 알림 (outbox)"]
        C1[[NOTIFICATION_REQUEST 적재\n원 트랜잭션에 참여]]
        C2["비동기 발송 파이프라인\n(폴링 10s → 발송 → SENT/RETRY_WAIT/DEAD)\n상세: async-design.md"]

        C1 --> C2
    end

    A6 -->|결제 확정| B1
    A10 -->|취소| B2
    A6 -.->|ENROLLMENT_CONFIRMED| C1
    A10 -.->|ENROLLMENT_CANCELLED| C1
    A12 -.->|WAITLIST_PROMOTED| C1
```

## 읽는 법

- **실선(→)**: 도메인 간 데이터 생성 (신청 도메인이 판매 레코드를 만듦)
- **점선(-.→)**: 이벤트 발행 → 알림 요청 적재 (동일 트랜잭션, 원자적)
- 다이어그램의 각 상태 이름(`PENDING`, `CONFIRMED` 등)은 도메인마다 별도 컬럼(`ENROLLMENT.status`, `SETTLEMENT.status`, `NOTIFICATION_REQUEST.status`)이며 서로 다른 값 집합이다 — 혼동 방지용으로 각 노드에 소속 컬럼을 명시했다
