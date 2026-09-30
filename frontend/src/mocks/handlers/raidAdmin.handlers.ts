import { HttpResponse, http } from 'msw';

import type {
  AdminBoss,
  AdminBossCreateRequest,
  AdminBossPhaseImageUpdateRequest,
  AdminBossPhaseTemplatesUpdateRequest,
  AdminBossRun,
  AdminBossRunActionRequest,
  AdminBossRunCreateRequest,
  AdminBossRunUpdateRequest,
  AdminBossStatusUpdateRequest,
  AdminBossUpdateRequest,
  AdminBook,
  AdminBookVisibilityUpdateRequest,
  BossRunStatus,
  BossStatus,
} from '@/features/raid-admin';

import { hasLibraryBook, replaceLibraryBookCover } from './library.handlers';

const initialBoss: AdminBoss = {
  id: 3,
  name: '새벽의 수호자',
  description: '새벽이 끝나기 전에 마음을 모으세요.',
  status: 'ACTIVE',
  bookCoverImageAssetId: 201,
  bookCoverImageUrl: '/images/book-covers/mock.webp',
  moods: [
    { code: 'CALM', displayName: '평온', role: 'THEME', weight: 1 },
    { code: 'JOY', displayName: '기쁨', role: 'WEAKNESS', weight: 1.2 },
  ],
  phaseTemplates: [{ id: 1, phaseNo: 1, maxHp: 1700, hardMinScore: null, recommendedScore: null, damageMultiplier: 1, imageAssetId: 101, imageUrl: '/images/mock.webp' }],
  createdAt: '2026-07-30T01:23:45Z',
  updatedAt: '2026-07-30T01:23:45Z',
};

const initialAdminBooks: AdminBook[] = [
  {
    bookId: 18,
    bossRunId: 7,
    bossName: '새벽의 수호자',
    status: 'COMPLETED',
    visibility: 'PUBLIC',
    coverImageAssetId: 201,
    coverImageUrl: '/images/book-covers/mock.webp',
    completedAt: '2026-07-30T10:05:00Z',
    contentCount: 8,
    updatedAt: '2026-07-30T10:05:00Z',
  },
  {
    bookId: 19,
    bossRunId: 8,
    bossName: '심연의 기록자',
    status: 'GENERATING',
    visibility: 'HIDDEN',
    coverImageAssetId: null,
    coverImageUrl: null,
    completedAt: null,
    contentCount: 0,
    updatedAt: '2026-07-31T02:00:00Z',
  },
];

let bosses = [initialBoss];
let runs: AdminBossRun[] = [];
let adminBooks = [...initialAdminBooks];

export function resetRaidAdminMockState() {
  bosses = [initialBoss];
  runs = [];
  adminBooks = [...initialAdminBooks];
}

/** 테스트가 상태별 레이드를 미리 심을 수 있게 한다. */
export function seedAdminBossRun(run: AdminBossRun) {
  runs = [run, ...runs.filter((existing) => existing.id !== run.id)];
}

const page = <T>(content: T[]) => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
  hasNext: false,
});

const conflict = (message = 'Boss was modified by another administrator.') =>
  HttpResponse.json({ code: 'CONFLICT', message, fieldErrors: [] }, { status: 409 });

const notFoundRun = () =>
  HttpResponse.json({ code: 'NOT_FOUND', message: 'Boss run not found.', fieldErrors: [] }, { status: 404 });

/** 서버처럼 expectedUpdatedAt을 비교한다. 어긋나면 409다. */
function findWritableRun(runId: number, expectedUpdatedAt: string | undefined) {
  const target = runs.find(({ id }) => id === runId);
  if (!target) return { error: notFoundRun() } as const;
  if (target.updatedAt !== expectedUpdatedAt) return { error: conflict('Boss run was modified.') } as const;
  return { run: target } as const;
}

function saveRun(updated: AdminBossRun) {
  runs = runs.map((run) => (run.id === updated.id ? updated : run));
  return HttpResponse.json(updated);
}

const notFound = () =>
  HttpResponse.json({ code: 'NOT_FOUND', message: 'Boss not found.', fieldErrors: [] }, { status: 404 });

/** 서버처럼 잠금 뒤 expectedUpdatedAt을 비교한다. 어긋나면 409다. */
function findWritableBoss(bossId: number, expectedUpdatedAt: string | undefined) {
  const target = bosses.find(({ id }) => id === bossId);
  if (!target) return { error: notFound() } as const;
  if (target.updatedAt !== expectedUpdatedAt) return { error: conflict() } as const;
  return { boss: target } as const;
}

function saveBoss(updated: AdminBoss) {
  bosses = bosses.map((boss) => (boss.id === updated.id ? updated : boss));
  return HttpResponse.json(updated);
}

export const raidAdminHandlers = [
  http.get('*/api/admin/bosses', ({ request }) => {
    const params = new URL(request.url).searchParams;
    const query = params.get('query');
    const status = params.get('status') as BossStatus | null;
    const filtered = bosses.filter(
      (boss) =>
        (!query || boss.name.toLowerCase().includes(query.toLowerCase())) &&
        (!status || boss.status === status),
    );
    return HttpResponse.json(page(filtered));
  }),
  http.patch('*/api/admin/bosses/:bossId', async ({ params, request }) => {
    const body = (await request.json()) as AdminBossUpdateRequest;
    const found = findWritableBoss(Number(params.bossId), body.expectedUpdatedAt);
    if ('error' in found) return found.error;
    if (!body.name?.trim() || !body.description?.trim() || !body.moods?.length) {
      return HttpResponse.json(
        { code: 'VALIDATION_ERROR', message: 'name, description and moods are required.', fieldErrors: [] },
        { status: 400 },
      );
    }
    return saveBoss({
      ...found.boss,
      name: body.name,
      description: body.description,
      moods: body.moods.map((mood) => ({ ...mood, displayName: mood.code })),
      updatedAt: new Date().toISOString(),
    });
  }),
  http.put('*/api/admin/bosses/:bossId/phase-templates', async ({ params, request }) => {
    const body = (await request.json()) as AdminBossPhaseTemplatesUpdateRequest;
    const found = findWritableBoss(Number(params.bossId), body.expectedUpdatedAt);
    if ('error' in found) return found.error;
    if (!body.phaseTemplates?.length) {
      return HttpResponse.json(
        { code: 'VALIDATION_ERROR', message: 'At least one phase template is required.', fieldErrors: [] },
        { status: 400 },
      );
    }
    return saveBoss({
      ...found.boss,
      phaseTemplates: body.phaseTemplates.map((phase, index) => ({
        id: index + 20,
        phaseNo: index + 1,
        maxHp: phase.maxHp ?? 1700,
        hardMinScore: phase.hardMinScore ?? null,
        recommendedScore: phase.recommendedScore ?? null,
        damageMultiplier: phase.damageMultiplier ?? 1,
        imageAssetId: phase.imageAssetId,
        imageUrl: `/images/replaced-${phase.imageAssetId}.webp`,
      })),
      updatedAt: new Date().toISOString(),
    });
  }),
  http.patch('*/api/admin/bosses/:bossId/status', async ({ params, request }) => {
    const body = (await request.json()) as AdminBossStatusUpdateRequest;
    const found = findWritableBoss(Number(params.bossId), body.expectedUpdatedAt);
    if ('error' in found) return found.error;
    return saveBoss({ ...found.boss, status: body.status, updatedAt: new Date().toISOString() });
  }),
  http.post('*/api/admin/bosses', async ({ request }) => {
    const body = (await request.json()) as AdminBossCreateRequest;
    // 서버는 표지 없이는 보스를 만들지 않는다. 목이 무엇이든 받아 주면 필드를 빠뜨린
    // 화면이 테스트를 통과하고 실제 환경에서만 400으로 실패한다.
    if (body.bookCoverImageAssetId == null) {
      return HttpResponse.json(
        { code: 'VALIDATION_ERROR', message: 'bookCoverImageAssetId is required.', fieldErrors: [] },
        { status: 400 },
      );
    }
    const boss: AdminBoss = {
      ...body,
      id: Math.max(0, ...bosses.map(({ id }) => id)) + 1,
      status: 'ACTIVE',
      bookCoverImageUrl: '/images/book-covers/mock.webp',
      moods: body.moods.map((mood) => ({ ...mood, displayName: mood.code })),
      phaseTemplates: body.phaseTemplates.map((phase, index) => ({
        ...phase,
        id: index + 10,
        phaseNo: index + 1,
        maxHp: phase.maxHp ?? 1700,
        hardMinScore: phase.hardMinScore ?? null,
        recommendedScore: phase.recommendedScore ?? null,
        damageMultiplier: phase.damageMultiplier ?? 1,
        imageUrl: '/images/mock.webp',
      })),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    bosses = [boss, ...bosses];
    return HttpResponse.json(boss, { status: 201 });
  }),
  http.put('*/api/admin/bosses/:bossId/book-cover', async ({ params, request }) => {
    const body = (await request.json()) as { imageAssetId?: number };
    if (body.imageAssetId == null) {
      return HttpResponse.json(
        { code: 'VALIDATION_ERROR', message: 'A book cover imageAssetId is required.', fieldErrors: [] },
        { status: 400 },
      );
    }
    const bossId = Number(params.bossId);
    const target = bosses.find(({ id }) => id === bossId);
    if (!target) return HttpResponse.json({ code: 'NOT_FOUND', message: 'Boss not found.' }, { status: 404 });
    // 서버도 표지 교체에서 updatedAt을 올린다. 목이 그대로 두면 이어지는 구획 저장이
    // 실제로는 409인데 테스트에서만 통과한다.
    const updated: AdminBoss = {
      ...target,
      bookCoverImageAssetId: body.imageAssetId,
      bookCoverImageUrl: `/images/book-covers/replaced-${body.imageAssetId}.webp`,
      updatedAt: new Date().toISOString(),
    };
    bosses = bosses.map((boss) => (boss.id === bossId ? updated : boss));
    return HttpResponse.json(updated);
  }),
  http.put('*/api/admin/books/:bookId/cover', async ({ params, request }) => {
    const body = (await request.json()) as { imageAssetId?: number };
    if (body.imageAssetId == null) {
      return HttpResponse.json(
        { code: 'VALIDATION_ERROR', message: 'A book cover imageAssetId is required.', fieldErrors: [] },
        { status: 400 },
      );
    }
    const bookId = Number(params.bookId);
    if (!hasLibraryBook(bookId)) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Book not found.' }, { status: 404 });
    }
    const coverImageUrl = `/images/book-covers/replaced-${body.imageAssetId}.webp`;
    // 목록 목에도 반영해야 교체 후 재조회가 실제로 바뀐 표지를 보여준다.
    replaceLibraryBookCover(bookId, coverImageUrl);
    // 서버는 표지 교체에서도 updatedAt을 올린다. 목이 그대로 두면 이어지는 공개 전환이
    // 실제로는 409인데 테스트에서만 통과한다.
    const updatedAt = new Date().toISOString();
    adminBooks = adminBooks.map((book) =>
      book.bookId === bookId
        ? { ...book, coverImageAssetId: body.imageAssetId ?? null, coverImageUrl, updatedAt }
        : book,
    );
    return HttpResponse.json({
      bookId,
      coverImageAssetId: body.imageAssetId,
      coverImageUrl,
      updatedAt,
    });
  }),
  http.get('*/api/admin/boss-runs', ({ request }) => {
    const params = new URL(request.url).searchParams;
    const query = params.get('query');
    const statuses = params.getAll('status');
    return HttpResponse.json(
      page(
        runs.filter(
          (run) =>
            (!query || run.bossName.toLowerCase().includes(query.toLowerCase())) &&
            (statuses.length === 0 || statuses.includes(run.status)),
        ),
      ),
    );
  }),
  http.patch('*/api/admin/boss-runs/:runId', async ({ params, request }) => {
    const body = (await request.json()) as AdminBossRunUpdateRequest;
    const found = findWritableRun(Number(params.runId), body.expectedUpdatedAt);
    if ('error' in found) return found.error;
    // 서버는 진행 중 회차에서 endsAt 외의 변경을 409로 거절한다.
    if (found.run.status === 'ACTIVE' && (body.bossId != null || body.phases != null)) {
      return conflict('An active run can only change endsAt.');
    }
    if (found.run.status !== 'ACTIVE' && found.run.status !== 'SCHEDULED') {
      return conflict('A terminal run is read-only.');
    }
    // 예정 회차 수정은 전체 교체다. 서버는 bossId·phases가 없으면 400을 낸다.
    if (found.run.status === 'SCHEDULED' && (body.bossId == null || !body.phases?.length)) {
      return HttpResponse.json(
        { code: 'VALIDATION_ERROR', message: 'bossId and phases are required.', fieldErrors: [] },
        { status: 400 },
      );
    }
    return saveRun({ ...found.run, endsAt: body.endsAt, updatedAt: new Date().toISOString() });
  }),
  http.post('*/api/admin/boss-runs/:runId/:action', async ({ params, request }) => {
    const action = String(params.action);
    if (!['activate', 'cancel', 'end'].includes(action)) return notFoundRun();
    const body = (await request.json()) as AdminBossRunActionRequest;
    const found = findWritableRun(Number(params.runId), body.expectedUpdatedAt);
    if ('error' in found) return found.error;

    const allowed: Record<string, BossRunStatus[]> = {
      activate: ['SCHEDULED'],
      cancel: ['SCHEDULED'],
      end: ['ACTIVE'],
    };
    if (!allowed[action].includes(found.run.status)) {
      return conflict(`The run cannot be ${action}d in ${found.run.status}.`);
    }
    const nextStatus: BossRunStatus =
      action === 'activate' ? 'ACTIVE' : action === 'cancel' ? 'CANCELLED' : 'ENDED_UNCLEARED';
    return saveRun({
      ...found.run,
      status: nextStatus,
      startedAt: action === 'activate' ? new Date().toISOString() : found.run.startedAt,
      updatedAt: new Date().toISOString(),
    });
  }),
  http.put('*/api/admin/boss-runs/:runId/phases/:phaseNo/image', async ({ params, request }) => {
    const body = (await request.json()) as AdminBossPhaseImageUpdateRequest;
    const found = findWritableRun(Number(params.runId), body.expectedUpdatedAt);
    if ('error' in found) return found.error;
    if (found.run.status !== 'SCHEDULED' && found.run.status !== 'ACTIVE') {
      return conflict('Only a scheduled or active boss run can change phase images.');
    }
    const phaseNo = Number(params.phaseNo);
    if (!found.run.phases.some((phase) => phase.phaseNo === phaseNo)) return notFoundRun();
    return saveRun({
      ...found.run,
      phases: found.run.phases.map((phase) =>
        phase.phaseNo === phaseNo
          ? {
              ...phase,
              imageAssetId: body.imageAssetId,
              imageUrl: `/images/replaced-${body.imageAssetId}.webp`,
            }
          : phase,
      ),
      updatedAt: new Date().toISOString(),
    });
  }),
  http.get('*/api/admin/books', ({ request }) => {
    const params = new URL(request.url).searchParams;
    const query = params.get('query');
    const statuses = params.getAll('status');
    const visibility = params.get('visibility');
    return HttpResponse.json(
      page(
        adminBooks.filter(
          (book) =>
            (!query || book.bossName.toLowerCase().includes(query.toLowerCase())) &&
            (statuses.length === 0 || statuses.includes(book.status)) &&
            (!visibility || book.visibility === visibility),
        ),
      ),
    );
  }),
  http.patch('*/api/admin/books/:bookId/visibility', async ({ params, request }) => {
    const body = (await request.json()) as AdminBookVisibilityUpdateRequest;
    const bookId = Number(params.bookId);
    const target = adminBooks.find((book) => book.bookId === bookId);
    if (!target) {
      return HttpResponse.json({ code: 'NOT_FOUND', message: 'Book not found.', fieldErrors: [] }, { status: 404 });
    }
    if (target.updatedAt !== body.expectedUpdatedAt) return conflict('Book was modified.');
    // 서버는 완료되지 않은 책의 공개 상태 변경을 양방향 모두 409로 거절한다.
    if (target.status !== 'COMPLETED') return conflict('Only a completed book can change visibility.');

    const updated: AdminBook = {
      ...target,
      visibility: body.visibility,
      updatedAt: new Date().toISOString(),
    };
    adminBooks = adminBooks.map((book) => (book.bookId === bookId ? updated : book));
    return HttpResponse.json(updated);
  }),
  http.post('*/api/admin/boss-images', () => HttpResponse.json({ imageAssetId: 101, imageUrl: '/images/mock.webp' }, { status: 201 })),
  http.post('*/api/admin/book-cover-images', () => HttpResponse.json({ imageAssetId: 201, imageUrl: '/images/book-covers/mock.webp' }, { status: 201 })),
  http.post('*/api/admin/boss-runs', async ({ request }) => {
    const body = (await request.json()) as AdminBossRunCreateRequest;
    const boss = bosses.find(({ id }) => id === body.bossId);
    if (!boss) return HttpResponse.json({ code: 'NOT_FOUND', message: 'Boss not found.' }, { status: 404 });
    const run: AdminBossRun = {
      id: Math.max(0, ...runs.map(({ id }) => id)) + 1,
      bossId: boss.id,
      bossName: boss.name,
      status: body.activate ? 'ACTIVE' : 'SCHEDULED',
      currentPhaseNo: 1,
      startedAt: body.activate ? new Date().toISOString() : (body.startsAt ?? null),
      endsAt: body.endsAt,
      phases: body.phases.map((phase, index) => ({
        phaseNo: index + 1,
        maxHp: phase.maxHp ?? 1700,
        remainingHp: phase.maxHp ?? 1700,
        hardMinScore: phase.hardMinScore ?? null,
        recommendedScore: phase.recommendedScore ?? null,
        damageMultiplier: phase.damageMultiplier ?? 1,
        imageAssetId: phase.imageAssetId,
        imageUrl: '/images/mock.webp',
        status: index === 0 && body.activate ? 'ACTIVE' : 'WAITING',
      })),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    runs = [run, ...runs];
    return HttpResponse.json(run, { status: 201 });
  }),
];
