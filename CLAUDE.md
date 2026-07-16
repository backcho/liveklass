# LiveKlass 채용 과제

퓨쳐스콜레(LiveKlass) 백엔드 채용 과제. 공식 요구는 택1이지만 A(수강신청)/B(정산)/C(알림) 전체 구현.

## 문서

- `docs/requirements.md` — **모든 정책 결정(SSOT)**. 항목 ID(G-*/A-*/B-*/C-*)로 코드·문서에서 참조된다
- `docs/erd.md` — 테이블 설계
- `docs/flow-overview.md` — A/B/C 도메인 간 이벤트 흐름
- `docs/async-design.md` — 알림 비동기 구조·재시도 정책 (과제 C 필수 제출물)
- `docs/conventions.md` — **코드 컨벤션·구조 스니펫** (코드 탐색 대신 이 문서로 스타일 파악)
- `docs/archive/` — 과제 원문, 진행 계획, phase별 작업 기록 등 개발 과정 산출물. 참고용이며 정책은 위 문서가 최신 기준

## 스택·환경

- Spring Boot 3.5.16 + Java 21(.sdkmanrc) + Gradle, MariaDB 10.11
- 전 구간 KST 고정(G-3): `TimeZoneConfig` + jackson time-zone + 컨테이너 TZ
- PK는 varchar — 샘플 데이터 id 호환(G-4)
- 실행: `docker-compose up -d` → `./gradlew bootRun --args='--spring.profiles.active=local'` (또는 `make up` / `make run`)
- 테스트: `./gradlew test` (Docker 필요 — Testcontainers)
- Swagger: http://localhost:8080/swagger-ui.html

## 컨벤션

상세 스니펫은 `docs/conventions.md` 참조 (새 패턴 생기면 갱신).

- 패키지: 도메인 우선 `com.liveklass.{user,course,enrollment,...}` (+`api/`, `dto/` 하위), 공통은 `common/`, 설정은 `config/`
- 에러: `ErrorCode` enum + `BusinessException` + `GlobalExceptionHandler` 공통 규격. Security 실패 응답도 동일 규격
- 인증: HTTP Basic(G-2). 시드 사용자 비밀번호 규칙 `{id}!` (예: creator-1!)
- 시드: `seed/sample-data.json` → `SeedDataLoader` (local 프로파일 전용)
- 정책 변경은 코드보다 `docs/requirements.md`를 먼저 갱신
- 커밋: 기능 단위, 한국어 요약
- `docs/`는 현재 git untracked(사용자 지시) — 제출 전 포함 재검토
