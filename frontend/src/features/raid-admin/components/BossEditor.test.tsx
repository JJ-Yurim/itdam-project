import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/shared/components';
import { server } from '@/mocks/server';

import type { AdminBoss } from '../types';
import { BossEditor } from './BossEditor';

const existingBoss: AdminBoss = {
  id: 3,
  name: '새벽의 수호자',
  description: '새벽이 끝나기 전에 마음을 모으세요.',
  status: 'ACTIVE',
  bookCoverImageAssetId: 201,
  bookCoverImageUrl: '/images/book-covers/mock.webp',
  moods: [{ code: 'CALM', displayName: '평온', role: 'THEME', weight: 1 }],
  phaseTemplates: [
    {
      id: 1,
      phaseNo: 1,
      maxHp: 1700,
      hardMinScore: null,
      recommendedScore: null,
      damageMultiplier: 1,
      imageAssetId: 101,
      imageUrl: '/images/mock.webp',
    },
  ],
  createdAt: '2026-07-30T01:23:45Z',
  updatedAt: '2026-07-30T01:23:45Z',
};

function renderEditor(props: { boss?: AdminBoss; onClose?: () => void } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <BossEditor boss={props.boss} onClose={props.onClose ?? (() => {})} />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

function imageFile(name: string) {
  return new File(['fake-image-bytes'], name, { type: 'image/webp' });
}

async function fillCreateFields(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/^이름/), '기억의 파수꾼');
  await user.type(screen.getByLabelText(/^설명/), '기억을 지키는 보스');
  await user.upload(screen.getByLabelText(/책 표지 이미지/), imageFile('cover.webp'));
  await user.upload(screen.getByLabelText('1번째 페이즈 이미지'), imageFile('phase.webp'));
}

describe('BossEditor 생성', () => {
  it('책 표지를 업로드하고 그 자산 ID를 생성 요청에 실어 보낸다', async () => {
    const user = userEvent.setup();
    let coverUploadCount = 0;
    let createBody: Record<string, unknown> | undefined;

    server.use(
      http.post('*/api/admin/book-cover-images', () => {
        coverUploadCount += 1;
        return HttpResponse.json(
          { imageAssetId: 555, imageUrl: '/images/book-covers/uploaded.webp' },
          { status: 201 },
        );
      }),
      http.post('*/api/admin/bosses', async ({ request }) => {
        createBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 9 }, { status: 201 });
      }),
    );

    renderEditor();
    await fillCreateFields(user);
    await user.click(screen.getByRole('button', { name: '보스 생성' }));

    // 서버는 표지 없이 보스를 만들지 않는다. 이 필드가 빠지면 400으로 끝난다.
    await waitFor(() => expect(createBody?.bookCoverImageAssetId).toBe(555));
    expect(coverUploadCount).toBe(1);
  });

  it('생성에 성공하면 패널을 닫는다', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    server.use(http.post('*/api/admin/bosses', () => HttpResponse.json({ id: 9 }, { status: 201 })));

    renderEditor({ onClose });
    await fillCreateFields(user);
    await user.click(screen.getByRole('button', { name: '보스 생성' }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it('표지를 고르지 않으면 어느 이미지가 빠졌는지 알려 준다', async () => {
    const user = userEvent.setup();
    let uploadCalled = false;
    server.use(
      http.post('*/api/admin/book-cover-images', () => {
        uploadCalled = true;
        return HttpResponse.json({ imageAssetId: 555, imageUrl: '/x.webp' }, { status: 201 });
      }),
    );

    renderEditor();
    await user.type(screen.getByLabelText(/^이름/), '기억의 파수꾼');
    await user.type(screen.getByLabelText(/^설명/), '기억을 지키는 보스');
    await user.upload(screen.getByLabelText('1번째 페이즈 이미지'), imageFile('phase.webp'));
    await user.click(screen.getByRole('button', { name: '보스 생성' }));

    expect(await screen.findByText(/책 표지 이미지를 선택/)).toBeInTheDocument();
    expect(uploadCalled).toBe(false);
  });

  it('표지 업로드가 실패하면 보스를 만들지 않고 입력값을 유지한다', async () => {
    const user = userEvent.setup();
    let createCalled = false;
    server.use(
      http.post('*/api/admin/book-cover-images', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR' }, { status: 500 }),
      ),
      http.post('*/api/admin/bosses', () => {
        createCalled = true;
        return HttpResponse.json({ id: 9 }, { status: 201 });
      }),
    );

    renderEditor();
    await fillCreateFields(user);
    await user.click(screen.getByRole('button', { name: '보스 생성' }));

    expect(await screen.findByText(/책 표지 이미지 업로드에 실패/)).toBeInTheDocument();
    expect(createCalled).toBe(false);
    // 다시 시도할 때 처음부터 채우게 만들면 안 된다.
    expect(screen.getByLabelText(/^이름/)).toHaveValue('기억의 파수꾼');
  });

  it('페이즈를 추가·삭제하고 순서를 바꿀 수 있다', async () => {
    const user = userEvent.setup();
    renderEditor();

    await user.click(screen.getByRole('button', { name: '페이즈 추가' }));
    expect(screen.getAllByRole('group', { name: /번째 페이즈$/ })).toHaveLength(2);

    await user.type(screen.getByLabelText('1번째 페이즈 최대 HP'), '1000');
    await user.type(screen.getByLabelText('2번째 페이즈 최대 HP'), '2000');
    await user.click(screen.getByRole('button', { name: '2번째 페이즈를 위로 이동' }));

    expect(screen.getByLabelText('1번째 페이즈 최대 HP')).toHaveValue(2000);
    expect(screen.getByLabelText('2번째 페이즈 최대 HP')).toHaveValue(1000);

    await user.click(screen.getByRole('button', { name: '2번째 페이즈 삭제' }));
    expect(screen.getAllByRole('group', { name: /번째 페이즈$/ })).toHaveLength(1);
    expect(screen.getByLabelText('1번째 페이즈 최대 HP')).toHaveValue(2000);
  });
});

describe('BossEditor 수정', () => {
  it('기존 값을 채우고 기본 정보만 PATCH로 저장한다', async () => {
    const user = userEvent.setup();
    let patchBody: Record<string, unknown> | undefined;
    server.use(
      http.patch('*/api/admin/bosses/:bossId', async ({ request }) => {
        patchBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...existingBoss, name: '수정된 보스' });
      }),
    );

    renderEditor({ boss: existingBoss });
    expect(screen.getByLabelText(/^이름/)).toHaveValue('새벽의 수호자');
    expect(screen.getByLabelText(/^설명/)).toHaveValue('새벽이 끝나기 전에 마음을 모으세요.');

    await user.clear(screen.getByLabelText(/^이름/));
    await user.type(screen.getByLabelText(/^이름/), '수정된 보스');
    await user.click(screen.getByRole('button', { name: '기본 정보 저장' }));

    await waitFor(() => expect(patchBody).toBeDefined());
    expect(patchBody).toEqual({
      name: '수정된 보스',
      description: '새벽이 끝나기 전에 마음을 모으세요.',
      moods: [{ code: 'CALM', role: 'THEME', weight: 1 }],
      expectedUpdatedAt: '2026-07-30T01:23:45Z',
    });
  });

  it('페이즈 템플릿 저장은 모든 페이즈의 새 이미지를 요구한다', async () => {
    const user = userEvent.setup();
    let putCalled = false;
    server.use(
      http.put('*/api/admin/bosses/:bossId/phase-templates', () => {
        putCalled = true;
        return HttpResponse.json(existingBoss);
      }),
    );

    renderEditor({ boss: existingBoss });
    // 서버는 새로 지정하는 페이즈 이미지를 방금 올린 자산으로만 받는다.
    expect(screen.getByText(/모든 페이즈의 이미지를 다시 선택/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '페이즈 템플릿 저장' }));
    expect(await screen.findByText(/모든 페이즈에 이미지를 선택/)).toBeInTheDocument();
    expect(putCalled).toBe(false);
  });

  it('페이즈 이미지를 올리고 템플릿을 교체한다', async () => {
    const user = userEvent.setup();
    let putBody: Record<string, unknown> | undefined;
    server.use(
      http.post('*/api/admin/boss-images', () =>
        HttpResponse.json({ imageAssetId: 777, imageUrl: '/images/new.webp' }, { status: 201 }),
      ),
      http.put('*/api/admin/bosses/:bossId/phase-templates', async ({ request }) => {
        putBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(existingBoss);
      }),
    );

    renderEditor({ boss: existingBoss });
    await user.upload(screen.getByLabelText('1번째 페이즈 이미지'), imageFile('phase.webp'));
    await user.click(screen.getByRole('button', { name: '페이즈 템플릿 저장' }));

    await waitFor(() => expect(putBody).toBeDefined());
    expect(putBody).toEqual({
      phaseTemplates: [{ maxHp: 1700, imageAssetId: 777 }],
      expectedUpdatedAt: '2026-07-30T01:23:45Z',
    });
  });

  it('기본 정보를 저장한 뒤 페이즈 템플릿도 이어서 저장할 수 있다', async () => {
    // 구획을 저장하면 서버 updatedAt이 바뀐다. 목록에서 받은 스냅샷을 계속 쓰면 두 번째
    // 저장이 자기 자신 때문에 409가 된다.
    const user = userEvent.setup();
    const sentExpectations: unknown[] = [];
    server.use(
      http.patch('*/api/admin/bosses/:bossId', async ({ request }) => {
        const body = (await request.json()) as { expectedUpdatedAt: string };
        sentExpectations.push(body.expectedUpdatedAt);
        if (body.expectedUpdatedAt !== existingBoss.updatedAt) {
          return HttpResponse.json({ code: 'CONFLICT' }, { status: 409 });
        }
        return HttpResponse.json({ ...existingBoss, updatedAt: '2026-08-06T02:00:00Z' });
      }),
      http.post('*/api/admin/boss-images', () =>
        HttpResponse.json({ imageAssetId: 777, imageUrl: '/images/new.webp' }, { status: 201 }),
      ),
      http.put('*/api/admin/bosses/:bossId/phase-templates', async ({ request }) => {
        const body = (await request.json()) as { expectedUpdatedAt: string };
        sentExpectations.push(body.expectedUpdatedAt);
        if (body.expectedUpdatedAt !== '2026-08-06T02:00:00Z') {
          return HttpResponse.json({ code: 'CONFLICT' }, { status: 409 });
        }
        return HttpResponse.json({ ...existingBoss, updatedAt: '2026-08-06T03:00:00Z' });
      }),
    );

    renderEditor({ boss: existingBoss });
    await user.click(screen.getByRole('button', { name: '기본 정보 저장' }));
    await waitFor(() => expect(sentExpectations).toHaveLength(1));

    await user.upload(screen.getByLabelText('1번째 페이즈 이미지'), imageFile('phase.webp'));
    await user.click(screen.getByRole('button', { name: '페이즈 템플릿 저장' }));

    await waitFor(() => expect(sentExpectations).toHaveLength(2));
    expect(sentExpectations).toEqual(['2026-07-30T01:23:45Z', '2026-08-06T02:00:00Z']);
    expect(screen.queryByText(/다른 관리자가/)).not.toBeInTheDocument();
  });

  it('다른 관리자가 먼저 수정했으면 최신 데이터를 다시 불러오도록 안내한다', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    server.use(
      http.patch('*/api/admin/bosses/:bossId', () =>
        HttpResponse.json(
          { code: 'CONFLICT', message: 'Boss was modified by another administrator.' },
          { status: 409 },
        ),
      ),
    );

    renderEditor({ boss: existingBoss, onClose });
    await user.click(screen.getByRole('button', { name: '기본 정보 저장' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/다른 관리자가/);
    await user.click(screen.getByRole('button', { name: '최신 데이터 다시 불러오기' }));
    expect(onClose).toHaveBeenCalled();
  });
});
