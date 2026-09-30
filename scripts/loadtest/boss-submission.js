// 보스 제출 동시성 부하 시나리오 (ISSUE-198).
//
// 하나의 진행 중 보스 회차에 N명이 동시에 제출한다. 모든 요청이 같은 boss_runs 행을
// SELECT FOR UPDATE로 잠그므로, VU 수를 올릴 때 처리량이 어떻게 꺾이는지가 측정 대상이다.
//
// 실행은 run.sh가 담당한다. 직접 실행할 때는 최소한 다음을 넘겨야 한다.
//   k6 run -e MANIFEST_PATH=/loadtest/manifest.json -e BASE_URL=http://host.docker.internal:8080 \
//          -e VUS=20 -e DURATION=60s boss-submission.js

import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const MANIFEST_PATH = __ENV.MANIFEST_PATH;
if (!MANIFEST_PATH) {
  throw new Error('MANIFEST_PATH is required. Generate it with manifest.sql first.');
}
const manifest = JSON.parse(open(MANIFEST_PATH));

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const PASSWORD = __ENV.LOADTEST_PASSWORD || 'LoadTest1!';
const VUS = Number(__ENV.VUS || 20);
const DURATION = __ENV.DURATION || '60s';

const submissionDuration = new Trend('submission_duration', true);
const submissionFailed = new Rate('submission_failed');
const submissionOk = new Counter('submission_ok');
// 아래 카운터가 0이 아니면 성능 결과가 아니라 환경·시드 문제다. 응답 시간 분포를 보기 전에
// 먼저 확인한다. 상태별로 나눠 두지 않으면 "전부 실패"만 보이고 원인을 좁힐 수 없다.
const submissionConflict = new Counter('submission_conflict');
const submissionRateLimited = new Counter('submission_rate_limited');
const submissionForbidden = new Counter('submission_forbidden');
const submissionRejected = new Counter('submission_rejected');
const submissionServerError = new Counter('submission_server_error');
const submissionTransportError = new Counter('submission_transport_error');

export const options = {
  scenarios: {
    submit: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      gracefulStop: '15s',
    },
  },
  thresholds: {
    // 실패가 섞인 실행의 응답 시간 분포는 비교 기준으로 쓸 수 없다. 이 임계값은 성능 목표가
    // 아니라 "이번 실행이 유효한 측정이었는지" 판별하는 게이트다.
    submission_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: false,
  // k6는 기본적으로 반복마다 쿠키 자를 비운다. 그러면 두 번째 반복부터 JSESSIONID와
  // XSRF-TOKEN이 사라져 VU마다 첫 제출만 성공하고 나머지는 전부 403이 된다.
  // 실제 사용자도 세션을 유지하며 여러 번 제출하므로 자를 VU 수명 동안 유지한다.
  noCookiesReset: true,
};

export function setup() {
  const accounts = manifest.users.length;
  if (accounts === 0) {
    fail('manifest has no seeded users. Run seed.sql first.');
  }
  // 한 계정을 두 VU가 나눠 쓰면 같은 문장 버전을 두 번 제출해 409 CONFLICT가 쏟아진다.
  // 그건 락 경합이 아니라 시드 부족이므로, 측정을 시작하기 전에 막는다.
  if (VUS > accounts) {
    fail(`VUS=${VUS} exceeds seeded users=${accounts}. Re-seed with -v users=${VUS} or lower VUS.`);
  }
  if (!manifest.bossRunId) {
    fail('manifest has no active bossRunId. Re-run seed.sql.');
  }
  return { bossRunId: manifest.bossRunId };
}

// VU 로컬 상태. k6는 VU마다 이 모듈 스코프를 따로 갖는다.
let account = null;
let cursor = 0;
let loggedFailure = false;

function csrfFromJar() {
  const cookies = http.cookieJar().cookiesForURL(`${BASE_URL}/`);
  const values = cookies['XSRF-TOKEN'];
  if (!values || values.length === 0) {
    fail('XSRF-TOKEN cookie is missing. Is BASE_URL pointing at the backend?');
  }
  return values[0];
}

function login() {
  // account는 로그인이 성공한 뒤에만 대입한다. 실패 시점에 이미 대입돼 있으면 fail()이 이
  // 반복만 끝내고 다음 반복의 `account === null` 검사가 거짓이 되어, 세션 없는 제출이 403으로
  // 쌓이며 원인(로그인 실패)이 CSRF 문제처럼 보인다.
  const candidate = manifest.users[(exec.vu.idInTest - 1) % manifest.users.length];

  // 로그인 자체가 POST라 먼저 토큰을 받아야 한다.
  const issued = http.get(`${BASE_URL}/api/v1/csrf`, { tags: { name: 'csrf' } });
  if (issued.status !== 200) {
    fail(`csrf issue failed: ${issued.status} ${issued.body}`);
  }

  const response = http.post(
    `${BASE_URL}/api/v1/auth/sessions`,
    JSON.stringify({ email: candidate.email, password: PASSWORD }),
    {
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfFromJar() },
      tags: { name: 'login' },
    },
  );
  if (response.status !== 200) {
    fail(`login failed for ${candidate.email}: ${response.status} ${response.body}`);
  }
  account = candidate;
}

export default function submit(data) {
  if (account === null) {
    login();
  }
  if (cursor >= account.versionIds.length) {
    // fail()은 이 반복만 끝내고 k6가 곧바로 다음 반복을 시작하므로, 남은 시간 동안 빈 반복이
    // 수십만 번 돌아 로그와 통계를 덮어 버린다. 시드가 부족한 실행은 결과가 없으니 즉시 멈춘다.
    exec.test.abort(`VU ${exec.vu.idInTest} exhausted ${account.versionIds.length} seeded versions. `
      + 'Re-seed with a larger --versions, or shorten --duration.');
  }
  const sentenceVersionId = account.versionIds[cursor];
  cursor += 1;

  const response = http.post(
    `${BASE_URL}/api/v1/boss-runs/${data.bossRunId}/submissions`,
    JSON.stringify({ sentenceVersionId, submissionConsentAccepted: true }),
    {
      // 토큰은 캐시하지 않고 매 요청 직전에 쿠키에서 읽는다. 서버는 SPA 패턴
      // (SpaCsrfTokenRequestHandler)을 쓰며 로그인 시 토큰을 회전시키므로, 어딘가에 담아 둔
      // 값보다 자의 최신 값을 쓰는 편이 회전 시점에 무관하게 안전하다.
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfFromJar() },
      tags: { name: 'submission' },
    },
  );

  submissionDuration.add(response.timings.duration);
  const created = response.status === 200 || response.status === 201;
  submissionFailed.add(!created);
  if (created) {
    submissionOk.add(1);
  } else {
    // 첫 실패의 본문을 한 번은 남긴다. 상태 코드만으로는 원인을 못 좁히는 경우가 많고,
    // 매 실패를 찍으면 로그가 통계를 덮는다.
    if (!loggedFailure) {
      loggedFailure = true;
      console.error(`submission failed: HTTP ${response.status} ${String(response.body).slice(0, 300)}`);
    }
    if (response.status === 0) submissionTransportError.add(1);
    else if (response.status === 403) submissionForbidden.add(1);
    else if (response.status === 409) submissionConflict.add(1);
    else if (response.status === 422 || response.status === 400) submissionRejected.add(1);
    else if (response.status === 429) submissionRateLimited.add(1);
    else if (response.status >= 500) submissionServerError.add(1);
  }

  check(response, {
    'submission accepted': () => created,
  });
}
