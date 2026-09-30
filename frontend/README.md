# 잇담 프론트엔드

Vite 기반 React·TypeScript 프론트엔드 프로젝트입니다.

## 필수 환경

- Node.js 24.18.0
- pnpm 11.17.0

Node.js를 설치한 뒤 VS Code를 완전히 종료하고 다시 실행합니다.

설치된 버전을 확인합니다.


```bash
node --version
npm --version
corepack --version
```

Node.js는 다음 버전이 표시되어야 합니다.

```text
v24.18.0
```

## 프로젝트 설치

저장소의 `frontend` 폴더로 이동합니다.

```bash
cd frontend
```

pnpm 버전을 확인합니다.

```bash
corepack pnpm --version
```

다음 버전이 표시되어야 합니다.

```text
11.17.0
```

프로젝트 의존성을 설치합니다.

```bash
corepack pnpm install --frozen-lockfile
```

Windows에서 `corepack enable pnpm` 실행 시 `EPERM` 권한 오류가 발생하더라도
`corepack pnpm` 명령을 사용하면 됩니다.

## 환경변수

`.env.example`을 복사해 로컬 `.env` 파일을 생성합니다.

Git Bash:

```bash
cp .env.example .env
```

PowerShell:

```powershell
Copy-Item .env.example .env
```

기본 로컬 API 주소:

```dotenv
VITE_API_BASE_URL=http://localhost:8080/api
VITE_ENABLE_MSW=false
```

`.env`에는 로컬 설정이나 비밀값이 들어갈 수 있으므로 Git에 커밋하지 않습니다.
환경변수 이름이나 예시가 변경되면 `.env.example`을 갱신합니다.

`VITE_ENABLE_MSW`는 개발 환경에서 Mock API를 사용할지 결정합니다.
실제 백엔드 API를 사용할 때는 `false`로 설정합니다.

## 개발 서버 실행

```bash
corepack pnpm dev
```

기본 접속 주소:

```text
http://localhost:5173
```

개발 서버를 종료할 때는 실행 중인 터미널에서 `Ctrl+C`를 누릅니다.

## 검증 명령

변경 사항을 제출하기 전에 다음 명령을 실행합니다.

```bash
corepack pnpm lint
corepack pnpm typecheck
corepack pnpm test
corepack pnpm build
corepack pnpm format:check
```

코드 형식을 자동으로 정리하려면 다음 명령을 실행합니다.

```bash
corepack pnpm format
```

테스트를 감시 모드로 실행하려면 다음 명령을 실행합니다.

```bash
corepack pnpm test:watch
```

## package scripts

| 명령           | 설명                                    |
| -------------- | --------------------------------------- |
| `dev`          | Vite 개발 서버 실행                     |
| `build`        | TypeScript 검사 후 프로덕션 build 생성  |
| `lint`         | ESLint 검사                             |
| `typecheck`    | TypeScript 타입 검사                    |
| `test`         | Vitest 전체 테스트 1회 실행             |
| `test:watch`   | Vitest 감시 모드 실행                   |
| `format`       | Prettier로 전체 파일 정리               |
| `format:check` | 파일을 수정하지 않고 Prettier 형식 검사 |
| `preview`      | 프로덕션 build 로컬 미리보기            |

## 주요 기술

- React
- TypeScript
- Vite
- pnpm
- Tailwind CSS
- ESLint
- Prettier
- Vitest
- React Testing Library
- React Router
- Axios
- TanStack Query
- React Hook Form
- Zod
- MSW

## 경로 Alias

`@/`는 `frontend/src/` 경로를 의미합니다.

```ts
import App from '@/App';
```

긴 상대 경로 대신 `@/` Alias를 사용합니다.

## 공통 애플리케이션 기반

Jira `ISSUE-82`에서 화면 개발에 공통으로 필요한 라우팅, API 통신,
서버 상태 관리, 공통 UI 및 Mock API 기반을 구성했습니다.

### 주요 디렉터리

```text
src/
├─ app/                  # 라우터, React Query, 전역 Provider
├─ mocks/                # 브라우저 및 테스트 Mock API
├─ pages/                # 화면 단위 컴포넌트
└─ shared/
   ├─ api/               # 공통 HTTP 클라이언트와 API 오류 처리
   ├─ components/        # 공통 UI 컴포넌트
   └─ layout/            # 공통 화면 레이아웃
```

### 라우팅

라우팅은 `src/app/router.tsx`에서 관리합니다.

| 경로        | 화면     | 공통 레이아웃 |
| ----------- | -------- | ------------- |
| `/`         | 홈       | 적용          |
| `/workshop` | 작업실   | 적용          |
| `/boss`     | 레이드   | 적용          |
| `/library`  | 도서관   | 적용          |
| `/archive`  | 내 서고  | 적용          |
| `/login`    | 로그인   | 미적용        |
| `/signup`   | 회원가입 | 미적용        |
| `*`         | 404      | 미적용        |

공통 레이아웃은 `src/shared/layout/AppLayout.tsx`에서 관리합니다. 모바일 화면 폭,
상단 헤더, 페이지 콘텐츠 영역과 하단 내비게이션이 포함되며 하위 페이지는
`Outlet` 위치에 렌더링됩니다.

현재 라우터에는 화면 구현 전 사용할 `PlaceholderPage`가 연결되어 있습니다.
화면 담당자는 `src/pages`에 페이지를 생성하고 라우터의 `element`를 실제 페이지
컴포넌트로 교체합니다.

### API 통신

공통 Axios 인스턴스는 `src/shared/api/httpClient.ts`에서 관리합니다.

- `VITE_API_BASE_URL`을 API 기본 주소로 사용
- 세션 쿠키 전달을 위해 `withCredentials: true` 적용
- 상태 변경 요청에 CSRF 토큰이 있으면 `X-XSRF-TOKEN` 헤더 추가

API 요청에는 개별 Axios 인스턴스를 만들지 않고 공통 `httpClient`를 사용합니다.

```ts
import { httpClient } from '@/shared/api';

const { data } = await httpClient.get('/v1/example');
```

기본 API 주소에 `/api`가 포함되어 있으므로 요청 경로에 `/api`를 중복해서
작성하지 않습니다.

```ts
// 올바른 예: /api/v1/example
httpClient.get('/v1/example');

// 잘못된 예: /api/api/v1/example
httpClient.get('/api/v1/example');
```

### CSRF 토큰

CSRF 토큰 조회와 메모리 저장 기능은 `src/shared/api/csrf.ts`와
`src/shared/api/csrfToken.ts`에서 관리합니다.

```ts
import { refreshCsrfToken } from '@/shared/api';

await refreshCsrfToken();
```

`refreshCsrfToken()`은 `GET /v1/csrf`를 호출하여 받은 토큰을 메모리에
저장합니다. 저장된 토큰은 `POST`, `PUT`, `PATCH`, `DELETE` 요청의
`X-XSRF-TOKEN` 헤더에 자동으로 포함됩니다.

현재 공통 기반에는 CSRF 조회, 저장 및 헤더 주입 기능만 포함되어 있습니다.
최초 발급 시점과 로그인 성공 후 재발급 처리는 인증 기능에서 연결합니다.

### API 오류 처리

백엔드 공통 오류 응답은 `ApiError` 형식으로 처리합니다.

```ts
interface ApiError {
  occurredAt: string;
  code: string;
  message: string;
  fieldErrors: FieldError[];
}
```

API 요청 실패 시 `getApiError()`를 사용하면 Axios 오류와 알 수 없는 오류를
공통 형식으로 변환할 수 있습니다.

```ts
import { getApiError } from '@/shared/api';

try {
  // API 요청
} catch (error) {
  const apiError = getApiError(error);
  console.error(apiError.message);
}
```

### 서버 상태 관리

React Query의 공통 `QueryClient`는 `src/app/queryClient.ts`에서 관리합니다.

- 조회 데이터 유효 시간: 30초
- 조회 실패 재시도: 1회
- 창 포커스 복귀 시 자동 재조회: 사용하지 않음
- 변경 요청 실패 재시도: 사용하지 않음

애플리케이션 최상단에 `QueryClientProvider`가 연결되어 있으므로 각 화면에서
`useQuery`와 `useMutation`을 사용할 수 있습니다.

### 공통 UI

공통 UI는 `src/shared/components`에서 관리합니다.

| 항목              | 역할                                                |
| ----------------- | --------------------------------------------------- |
| `Button`          | 기본 버튼과 `primary`, `secondary`, `danger` 스타일 |
| `LoadingState`    | 로딩 상태 표시                                      |
| `ErrorState`      | 오류 상태와 선택적 재시도 버튼 표시                 |
| `EmptyState`      | 데이터가 없는 상태 표시                             |
| `Modal`           | 네이티브 `dialog` 기반 모달                         |
| `ToastProvider`   | 전역 토스트 상태 및 화면 출력                       |
| `useToast`        | 컴포넌트에서 토스트 호출                            |
| `PlaceholderPage` | 실제 화면 구현 전 임시 페이지                       |

공통 UI는 공개 진입점을 통해 가져옵니다.

```ts
import { Button, ErrorState, Modal, useToast } from '@/shared/components';
```

`useToast`는 `ToastProvider` 내부에서만 사용할 수 있습니다. 현재
`AppProviders`에 전역으로 연결되어 있습니다.

### Mock API

MSW는 실제 백엔드 API가 준비되지 않았을 때 가짜 응답으로 화면 개발과 테스트를
진행하기 위한 도구입니다.

```text
src/mocks/browser.ts
src/mocks/server.ts
src/mocks/handlers/index.ts
tests/setup.ts
public/mockServiceWorker.js
```

Mock 응답은 `src/mocks/handlers/index.ts`에 추가합니다. 현재는 공통 실행 기반만
구성되어 있어 Mock 핸들러 배열이 비어 있습니다.

브라우저 개발 환경에서 Mock API를 사용하려면 로컬 `.env`에 다음 값을 설정합니다.

```dotenv
VITE_ENABLE_MSW=true
```

실제 백엔드 API를 사용할 때는 `false`로 설정합니다.

`public/mockServiceWorker.js`는 MSW가 자동 생성하는 파일이므로 직접 수정하지
않습니다. 다시 생성해야 한다면 다음 명령을 사용합니다.

```bash
corepack pnpm exec msw init public --save
```

### 화면 구현 순서

1. `src/pages`에 페이지 컴포넌트를 생성합니다.
2. `src/app/router.tsx`의 `PlaceholderPage`를 실제 페이지로 교체합니다.
3. API 요청에는 `@/shared/api`의 `httpClient`를 사용합니다.
4. 서버에서 가져온 상태는 React Query로 관리합니다.
5. 공통 UI는 `@/shared/components`에서 가져옵니다.
6. 백엔드가 준비되지 않았다면 `src/mocks/handlers/index.ts`에 Mock 응답을
   추가합니다.

### 구현 현황

초기 골격 단계의 "후속 연결 항목"(로그인·회원가입 화면, 인증 API, CSRF 발급, 보호 라우트,
도메인별 API 함수·React Query 키, MSW 핸들러)은 모두 구현 완료됐다. 현재는
`src/features/` 아래 10개 도메인(auth, daily-words, sentences, archive, boss, feed,
book-generation, library, raid-admin, settings)이 각각 api·queries·components를 갖춘다.

## 테스트

공통 테스트 환경은 다음 파일에서 설정합니다.

```text
tests/setup.ts
```

현재 공통 렌더링 확인 테스트는 다음 파일에 있습니다.

```text
tests/smoke.test.tsx
```

기능별 테스트는 대상 코드 가까이에 `.test.ts` 또는 `.test.tsx` 확장자로 작성합니다.

## 와이어프레임

화면별 정적 와이어프레임은 다음 경로에서 확인합니다.

```text
../docs/wireframes
```

와이어프레임 HTML과 CSS는 디자인 참고용이며 React 구현 파일이 아닙니다. 기능 개발
중에는 직접 수정하지 않습니다.

## 주의 사항

- `node_modules`와 `dist`는 Git에 커밋하지 않습니다.
- `pnpm-lock.yaml`은 반드시 Git에 커밋합니다.
- npm, yarn과 혼용하거나 다른 lockfile을 추가하지 않습니다.
- 실제 비밀값이 들어간 `.env` 파일을 Git에 커밋하지 않습니다.
- 공통 설정 파일과 공통 컴포넌트는 팀원과 협의한 뒤 변경합니다.
