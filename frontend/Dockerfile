# deps → verify / build → runtime 순서의 멀티스테이지 구성.
# Jenkins는 검증 단계에서 `--target verify`로 빌드한 뒤 컨테이너를 실행하고,
# 배포 이미지는 타깃 없이 빌드해 마지막 runtime 스테이지를 얻는다.
# 두 경로가 같은 deps 레이어를 공유하므로 의존성 설치는 한 번만 수행된다.

FROM node:24.18.0-alpine AS deps

ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0 \
    CI=true

WORKDIR /app

RUN corepack enable

# pnpm-workspace.yaml의 allowBuilds 설정이 postinstall 허용 범위를 결정하므로
# 잠금 파일과 함께 복사해야 로컬과 동일한 결과가 나온다.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .


FROM deps AS verify

# 로컬 개발은 Git에 없는 `.env`에서 이 값을 읽는다. CI 체크아웃에는 그 파일이
# 없으므로, httpClient의 baseURL이 undefined가 되어 MSW 핸들러가 요청을
# 가로채지 못하는 것을 막기 위해 배포와 같은 값을 직접 지정한다.
ENV VITE_API_BASE_URL=/api

# JUnit XML은 /reports에 기록하고 Jenkins가 docker cp로 꺼낸다.
# (컨테이너화된 Jenkins에서는 -v 경로가 호스트 기준으로 해석되어 볼륨을 못 쓴다.)
RUN mkdir -p /reports
CMD ["sh", "-c", "pnpm lint && pnpm typecheck && pnpm test --reporter=default --reporter=junit --outputFile.junit=/reports/vitest.xml"]


FROM deps AS build

# Vite는 빌드 타임에 값을 인라인하므로 배포 대상 API 경로를 빌드 인자로 받는다.
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN test -n "$VITE_API_BASE_URL" || (echo "VITE_API_BASE_URL build arg is required" >&2; exit 1)

RUN pnpm build


FROM nginx:1.27-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

# MSW 목 워커는 `frontend/public/`에 있어 dist에 그대로 포함된다. 프로덕션
# 빌드에는 등록 경로가 없지만 서빙할 이유도 없으므로 런타임에서 제외한다.
RUN rm -f /usr/share/nginx/html/mockServiceWorker.js

EXPOSE 80

HEALTHCHECK --interval=5s --timeout=3s --start-period=5s --retries=12 \
    CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
