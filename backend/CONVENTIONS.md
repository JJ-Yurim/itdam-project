# 백엔드 코드 규약

ISSUE-63에서 정립한 규약이다. 새 코드는 이 규약을 따르고, 기존 코드는 -64(대형 도메인)·-65(integration 경계) 리팩터링에서 순차적으로 맞춘다.

## 1. 패키지 구조

도메인 패키지는 다음 하위 구조를 따른다.

```
<domain>/
  api/          컨트롤러, API 계약 인터페이스, 요청·응답 DTO
  service/      비즈니스 로직
  repository/   Spring Data 리포지토리
  domain/       엔티티, 값 객체, 도메인 enum
```

- 하위 도메인이 충분히 크면(예: `boss/submission`) 같은 4계층 구조로 중첩한다. **부모↔하위 도메인은 같은 도메인으로 본다** — 부모가 하위의 리포지토리를 쓰는 것은 위반이 아니다 (2026-07-29 결정).
- 전역 관심사는 `common/`에 둔다: `common/api`(응답·예외), `common/config`(설정), `common/time`(시계), `common/security`(인증 주체 등 전역 보안 타입).
- **전역 인프라 설정(보안·Jackson·OpenAPI 등)은 도메인 패키지가 아닌 `common/config`에 둔다.**
- `integration/<외부시스템>/`은 외부 연동 어댑터 계층이다. 포트 인터페이스·전송 DTO·구현체(Fake 포함)를 담고, 4계층 구조를 따르지 않는다. 도메인 → integration 인터페이스 방향으로만 의존한다.
- 도메인 국소 정책 설정(`DamagePolicyConfiguration` 등)은 해당 도메인 `service/`에 둘 수 있다.
- 애플리케이션 루트 패키지에는 `BackendApplication`만 둔다. 임시 컨트롤러 금지.
- `main`의 Fake/Logging 구현체는 실제 구현체와 `@ConditionalOnProperty` 스위치로 교체된다. **기본값은 항상 외부 의존이 없는 쪽**(fake·logging·sync·in-process)이고, 배포 프로필·환경변수가 실제 구현을 켠다. 스위치 전체 목록은 `docs/README.md`의 "어댑터 경계" 참조.

## 2. 계층 규칙

- 컨트롤러는 서비스만 호출한다. 리포지토리 직접 호출 금지.
- 서비스는 **자기 도메인의 리포지토리만** 주입한다. 다른 도메인 데이터가 필요하면 그 도메인의 **경계 서비스**를 호출한다 (예: `EvaluatedSentenceQueryService`, `BossRunReader`, `BookInclusionQueryService`).
- 리포지토리(JPQL 포함)는 다른 도메인 엔티티를 **필터 조건으로** 사용하지 않는다. 조건이 필요하면 호출자가 식별자 목록으로 넘긴다 (예: 제출된 버전 제외 목록). 단, **스키마에 이미 존재하는 연관관계의 join fetch(N+1 회피)는 허용**한다 (2026-07-29 결정).
- 예외: `@Profile` 가드된 픽스처(`BossFixture` 등)는 테스트 데이터 구성을 위해 다른 도메인 리포지토리를 사용할 수 있다.
- 엔티티는 컨트롤러까지 나가지 않는다. 서비스가 응답 DTO로 변환해 반환한다. 경계 서비스가 다른 도메인 **서비스**에 엔티티를 반환하는 것은 허용한다 (FK 연관 구성 등).
- 서비스 계층 record(`~Result` 등)는 컨트롤러가 `~Response.from()`으로 마감해도 된다 — 엔티티가 아니므로 지연 로딩 위험이 없다.

## 3. API 규약

- 업무 API 경로는 `/api/v1` 프리픽스를 사용하고, 하나의 리소스 프리픽스는 기본적으로 하나의 컨트롤러가 소유한다. **다른 도메인이 하위 리소스를 제공할 때는 하위 경로 전체를 그 도메인 컨트롤러가 소유할 수 있다** (예: `/boss-runs/{id}/book-generation`은 book, `/sentence-versions/{id}/evaluations`는 evaluation 소유).
- API 계약(OpenAPI 애노테이션, `@Valid`)은 `~Api` 인터페이스에 선언하고 컨트롤러가 구현한다.
- 요청 본문 검증은 Bean Validation(`@NotBlank`, `@Size` 등)으로 선언한다. 인터페이스 선언 `@Valid`가 동작함은 `RequestValidationTest`로 보증된다.
- **페이지 응답은 `common/api/PageResponse<T>`를 사용한다.** 도메인별 페이지 record를 새로 만들지 않는다.
- 오류 응답은 `ApiError`(occurredAt·code·message·fieldErrors) 단일 형식이다. 날짜·시각은 ISO-8601로 직렬화된다.
- 성공 응답은 타입이 명시된 record DTO를 사용한다. `Map<String, String>` 반환 금지.

## 4. 예외 처리

- 업무 오류는 `DomainException(ErrorCode)`를 던진다. 상태 코드 매핑은 `ErrorCode`가 단일 출처다.
- 새 오류 유형이 필요하면 `ErrorCode`에 추가한다. 컨트롤러·서비스에서 `ResponseEntity`로 직접 상태 코드를 만들지 않는다.
- **인증 주체 검증은 `AuthenticatedUsers.requireUserId(principal)`을 사용한다.** principal null 검사를 복붙하지 않는다.
- 예외 메시지는 **영어**로 작성한다 (로그·클라이언트 처리 일관성). 사용자 표시용 한국어 메시지는 프런트엔드가 code 기반으로 매핑한다.

## 5. DTO ↔ Entity 변환

- 응답 DTO는 record로 정의하고, 정적 팩터리 `from(Entity)`를 DTO 쪽에 둔다.
- 요청 DTO → 엔티티 생성은 서비스에서 수행한다.
- 여러 DTO가 같은 구조를 반복하면 공통화를 먼저 검토한다 (예: `PageResponse`).

## 6. 네이밍

| 대상 | 규칙 | 예 |
|---|---|---|
| 조회 전용 서비스 | `~QueryService` (`@Transactional(readOnly = true)`). 예외: `REQUIRES_NEW`로 분리된 지연 만료(lazy expiry) 전이는 조회 흐름에서 허용 | `BossQueryService` |
| 명령 서비스 | `~CommandService` 또는 유스케이스 명칭의 `~Service` | `SentenceCommandService` |
| 트랜잭션 경계 클래스 | `~Transaction` — `@Transactional` 경계 메서드만 가진다 (같은 유스케이스의 성공·거부·실패 등 복수 경계는 허용). 시계 조회 등 부수 메서드 금지 | `BossSubmissionTransaction` |
| API 계약 인터페이스 | `~Api` | `SentenceApi` |
| 응답 DTO | `~Response`, 목록 항목은 `~Item` 또는 `~Summary` | `BossSubmissionHistoryItem` |

- import는 항상 명시한다. **FQCN 인라인 사용 금지.**
- 들여쓰기는 탭. 같은 값을 두 이름으로 노출하는 접근자 금지.

## 7. 시간

- 현재 시각은 항상 `ServiceClock`을 통해 얻는다. `Instant.now()`, `LocalDate.now()` 직접 호출 금지 (테스트 재현성).
- 예외: JPA 감사 콜백(`@PrePersist`/`@PreUpdate`)의 생성·수정 시각 기본값은 허용한다. 단 null-guard를 둬 도메인 로직이 시각을 명시적으로 넣을 수 있게 한다.
- 하나의 요청 안에서는 하나의 시각을 공유한다 (예: `BossSubmissionService`의 requestTime 패턴).

## 8. 테스트 계층

| 계층 | 조건 | 실행 |
|---|---|---|
| 단위 | Spring 컨텍스트 불필요한 순수 로직 | `test` 태스크 |
| 통합 (H2) | `@SpringBootTest` + `@ActiveProfiles("test")` | `test` 태스크 |
| 인프라 (Testcontainers) | Docker 필요. **`@Tag("infra")` 필수** | `infraTest` 태스크 (CI 전용) |

- 순수 도메인 규칙(계산기·정책)은 단위 테스트로 작성한다. 컨텍스트 기동 최소화.
- 설정 파일의 **문자열을 리터럴 비교하는 테스트 금지.** 동작(프로필 기동, 실제 응답)으로 검증한다.
- 테스트 더블(Fake) 자체를 검증하는 테스트를 만들지 않는다.
- 공유 H2 특성상 테스트 데이터 식별자는 고유값(시퀀스·nanoTime)을 사용한다. (격리 개선은 후속 과제)

## 9. Flyway 마이그레이션

- 파일명: `V{순번}__{snake_case_설명}.sql` (예: `V2__add_book_cover_column.sql`)
- 순번은 정수 증가. **머지 전에 develop의 최신 순번과 충돌하지 않는지 확인한다** (병행 브랜치 주의).
- 적용된 마이그레이션은 수정하지 않는다. 변경은 새 버전으로 추가한다.
- 참조(시드) 데이터도 마이그레이션으로 관리하는 것을 지향한다. (현재 `MoodCategorySeeder`는 -65에서 재검토)
