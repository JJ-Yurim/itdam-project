import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import { getLibraryBooks } from '@/features/library';

import {
  changeAdminBookCover,
  changeAdminBossRunPhaseImage,
  createAdminBossRun,
  getAdminBooks,
  getAdminBosses,
  getAdminBossRuns,
  getAllActiveAdminBosses,
  runAdminBossRunAction,
  updateAdminBoss,
  updateAdminBookVisibility,
  updateAdminBossPhaseTemplates,
  updateAdminBossRun,
  updateAdminBossStatus,
} from './api';

describe('레이드 관리자 API', () => {
  it('보스 목록의 페이지 파라미터를 전달한다', async () => {
    let query = '';
    server.use(http.get('*/api/admin/bosses', ({ request }) => {
      query = new URL(request.url).search;
      return HttpResponse.json({ content: [], page: 2, size: 10, totalElements: 0, totalPages: 0, hasNext: false });
    }));

    await getAdminBosses({ page: 2, size: 10 });
    expect(query).toBe('?page=2&size=10');
  });

  it('보스 목록의 검색어와 상태 필터를 전달하고 빈 값은 보내지 않는다', async () => {
    const queries: string[] = [];
    server.use(http.get('*/api/admin/bosses', ({ request }) => {
      queries.push(new URL(request.url).search);
      return HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false });
    }));

    await getAdminBosses({ query: '수호자', status: 'INACTIVE', page: 0, size: 20 });
    await getAdminBosses({ query: '', page: 0, size: 20 });

    expect(queries[0]).toContain('query=%EC%88%98%ED%98%B8%EC%9E%90');
    expect(queries[0]).toContain('status=INACTIVE');
    expect(queries[1]).toBe('?page=0&size=20');
  });

  it('레이드 생성 선택지는 활성 보스만 모은다', async () => {
    const statuses: (string | null)[] = [];
    server.use(http.get('*/api/admin/bosses', ({ request }) => {
      statuses.push(new URL(request.url).searchParams.get('status'));
      return HttpResponse.json({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false });
    }));

    await getAllActiveAdminBosses();
    expect(statuses).toEqual(['ACTIVE']);
  });

  it('보스 정보 수정을 PATCH로 계약 그대로 보낸다', async () => {
    let method = '';
    let pathname = '';
    let body: unknown;
    server.use(http.patch('*/api/admin/bosses/:bossId', async ({ request }) => {
      method = request.method;
      pathname = new URL(request.url).pathname;
      body = await request.json();
      return HttpResponse.json({ id: 3 });
    }));
    const request = {
      name: '수정된 보스',
      description: '수정된 설명',
      moods: [{ code: 'CALM', role: 'THEME' as const, weight: 1 }],
      expectedUpdatedAt: '2026-08-05T01:00:00Z',
    };

    await updateAdminBoss(3, request);

    expect(method).toBe('PATCH');
    expect(pathname).toBe('/api/admin/bosses/3');
    expect(body).toEqual(request);
  });

  it('페이즈 템플릿 교체를 PUT으로 보낸다', async () => {
    let method = '';
    let pathname = '';
    let body: unknown;
    server.use(http.put('*/api/admin/bosses/:bossId/phase-templates', async ({ request }) => {
      method = request.method;
      pathname = new URL(request.url).pathname;
      body = await request.json();
      return HttpResponse.json({ id: 3 });
    }));
    const request = {
      phaseTemplates: [{ maxHp: 1800, imageAssetId: 101 }],
      expectedUpdatedAt: '2026-08-05T01:00:00Z',
    };

    await updateAdminBossPhaseTemplates(3, request);

    expect(method).toBe('PUT');
    expect(pathname).toBe('/api/admin/bosses/3/phase-templates');
    expect(body).toEqual(request);
  });

  it('보스 상태 전환을 PATCH로 보낸다', async () => {
    let pathname = '';
    let body: unknown;
    server.use(http.patch('*/api/admin/bosses/:bossId/status', async ({ request }) => {
      pathname = new URL(request.url).pathname;
      body = await request.json();
      return HttpResponse.json({ id: 3, status: 'INACTIVE' });
    }));

    await updateAdminBossStatus(3, {
      status: 'INACTIVE',
      expectedUpdatedAt: '2026-08-05T01:00:00Z',
    });

    expect(pathname).toBe('/api/admin/bosses/3/status');
    expect(body).toEqual({ status: 'INACTIVE', expectedUpdatedAt: '2026-08-05T01:00:00Z' });
  });

  it('다른 관리자가 먼저 수정했으면 409로 끝난다', async () => {
    await expect(
      updateAdminBoss(3, {
        name: '수정된 보스',
        description: '수정된 설명',
        moods: [{ code: 'CALM', role: 'THEME', weight: 1 }],
        expectedUpdatedAt: '2000-01-01T00:00:00Z',
      }),
    ).rejects.toMatchObject({ response: { status: 409 } });
  });

  it('책 표지를 교체하면 도서관 목록에도 새 표지가 실린다', async () => {
    const before = await getLibraryBooks({ page: 0, size: 10 });
    const target = before.content[0]!;

    const result = await changeAdminBookCover(target.bookId, 555);

    expect(result.coverImageAssetId).toBe(555);
    const after = await getLibraryBooks({ page: 0, size: 10 });
    expect(after.content[0]?.coverImageUrl).toBe(result.coverImageUrl);
    expect(after.content[0]?.coverImageUrl).not.toBe(target.coverImageUrl);
  });

  it('없는 책의 표지 교체는 404로 끝난다', async () => {
    await expect(changeAdminBookCover(999_999, 555)).rejects.toMatchObject({
      response: { status: 404 },
    });
  });

  it('레이드 목록의 상태 필터를 status 파라미터 반복으로 보낸다', async () => {
    let search = '';
    server.use(http.get('*/api/admin/boss-runs', ({ request }) => {
      search = new URL(request.url).search;
      return HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false });
    }));

    await getAdminBossRuns({ statuses: ['SCHEDULED', 'ACTIVE'], page: 0, size: 20 });

    // Spring의 List 바인딩은 대괄호 없는 반복 파라미터를 읽는다.
    expect(search).toContain('status=SCHEDULED&status=ACTIVE');
    expect(search).not.toContain('status[]');
  });

  it('레이드 수정·상태 전환·페이즈 이미지 교체 경로와 본문을 계약대로 보낸다', async () => {
    const calls: { method: string; pathname: string; body: unknown }[] = [];
    const record = async ({ request }: { request: Request }) => {
      calls.push({
        method: request.method,
        pathname: new URL(request.url).pathname,
        body: await request.json(),
      });
      return HttpResponse.json({ id: 7 });
    };
    server.use(
      http.patch('*/api/admin/boss-runs/:runId', record),
      http.post('*/api/admin/boss-runs/:runId/:action', record),
      http.put('*/api/admin/boss-runs/:runId/phases/:phaseNo/image', record),
    );

    await updateAdminBossRun(7, { endsAt: '2026-08-08T00:00:00Z', expectedUpdatedAt: 'now' });
    await runAdminBossRunAction(7, 'cancel', { expectedUpdatedAt: 'now' });
    await changeAdminBossRunPhaseImage(7, 2, { imageAssetId: 321, expectedUpdatedAt: 'now' });

    expect(calls).toEqual([
      {
        method: 'PATCH',
        pathname: '/api/admin/boss-runs/7',
        body: { endsAt: '2026-08-08T00:00:00Z', expectedUpdatedAt: 'now' },
      },
      {
        method: 'POST',
        pathname: '/api/admin/boss-runs/7/cancel',
        body: { expectedUpdatedAt: 'now' },
      },
      {
        method: 'PUT',
        pathname: '/api/admin/boss-runs/7/phases/2/image',
        body: { imageAssetId: 321, expectedUpdatedAt: 'now' },
      },
    ]);
  });

  it('관리자 책 목록의 필터를 전달하고 공개 상태 전환을 PATCH로 보낸다', async () => {
    let search = '';
    server.use(http.get('*/api/admin/books', ({ request }) => {
      search = new URL(request.url).search;
      return HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false });
    }));

    await getAdminBooks({ query: '수호자', statuses: ['COMPLETED'], visibility: 'HIDDEN' });
    expect(search).toContain('status=COMPLETED');
    expect(search).toContain('visibility=HIDDEN');

    const updated = await updateAdminBookVisibility(18, {
      visibility: 'HIDDEN',
      expectedUpdatedAt: '2026-07-30T10:05:00Z',
    });
    expect(updated.visibility).toBe('HIDDEN');
  });

  it('완료되지 않은 책의 공개 상태 전환은 409로 끝난다', async () => {
    await expect(
      updateAdminBookVisibility(19, {
        visibility: 'PUBLIC',
        expectedUpdatedAt: '2026-07-31T02:00:00Z',
      }),
    ).rejects.toMatchObject({ response: { status: 409 } });
  });

  it('레이드 회차 생성 요청을 계약 그대로 전달한다', async () => {
    let receivedBody: unknown;
    server.use(http.post('*/api/admin/boss-runs', async ({ request }) => {
      receivedBody = await request.json();
      return HttpResponse.json({ id: 1 }, { status: 201 });
    }));
    const request = {
      bossId: 3,
      endsAt: '2026-08-06T15:00:00Z',
      activate: true,
      phases: [{ maxHp: 2000, hardMinScore: 40, recommendedScore: 60, damageMultiplier: 1.2, imageAssetId: 101 }],
    };

    await createAdminBossRun(request);
    expect(receivedBody).toEqual(request);
  });
});
