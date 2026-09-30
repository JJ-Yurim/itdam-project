import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/shared/components';
import { server } from '@/mocks/server';

import { CoverReplaceControl } from './CoverReplaceControl';

function renderControl(
  onReplace: (imageAssetId: number) => Promise<unknown>,
  cover: string | null = '/a.webp',
) {
  render(
    <ToastProvider>
      <CoverReplaceControl currentImageUrl={cover} targetName="새벽의 수호자" onReplace={onReplace} />
    </ToastProvider>,
  );
}

const imageFile = () => new File(['x'], 'cover.webp', { type: 'image/webp' });
const coverInput = () =>
  screen.getByLabelText('새벽의 수호자 표지 교체', { selector: 'input' }) as HTMLInputElement;
const replaceButton = () => screen.getByRole('button', { name: '새벽의 수호자 표지 교체' });

describe('CoverReplaceControl', () => {
  it('업로드한 자산 ID로 교체를 요청하고 파일 입력을 비운다', async () => {
    const user = userEvent.setup();
    const onReplace = vi.fn().mockResolvedValue(undefined);
    server.use(
      http.post('*/api/admin/book-cover-images', () =>
        HttpResponse.json({ imageAssetId: 777, imageUrl: '/b.webp' }, { status: 201 }),
      ),
    );

    renderControl(onReplace);
    await user.upload(coverInput(), imageFile());
    await user.click(replaceButton());

    await waitFor(() => expect(onReplace).toHaveBeenCalledWith(777));
    // 상태만 비우면 DOM에 파일명이 남아, 표지가 골라진 것처럼 보이는데 다시 누르면 막힌다.
    await waitFor(() => expect(coverInput().files).toHaveLength(0));
  });

  it('교체만 실패하면 다시 눌러도 같은 파일을 또 올리지 않는다', async () => {
    const user = userEvent.setup();
    let uploadCount = 0;
    const onReplace = vi
      .fn()
      .mockRejectedValueOnce(new Error('replace failed'))
      .mockResolvedValueOnce(undefined);
    server.use(
      http.post('*/api/admin/book-cover-images', () => {
        uploadCount += 1;
        return HttpResponse.json({ imageAssetId: 777, imageUrl: '/b.webp' }, { status: 201 });
      }),
    );

    renderControl(onReplace);
    await user.upload(coverInput(), imageFile());
    await user.click(replaceButton());
    await screen.findByText(/표지를 교체하지 못했습니다/);

    await user.click(replaceButton());

    await waitFor(() => expect(onReplace).toHaveBeenCalledTimes(2));
    // 두 번째 시도는 이미 올라간 자산을 다시 쓴다. 재업로드하면 참조 없는 자산이 쌓인다.
    expect(uploadCount).toBe(1);
  });

  it('업로드가 실패하면 교체를 시도하지 않는다', async () => {
    const user = userEvent.setup();
    const onReplace = vi.fn();
    server.use(
      http.post('*/api/admin/book-cover-images', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR' }, { status: 500 }),
      ),
    );

    renderControl(onReplace);
    await user.upload(coverInput(), imageFile());
    await user.click(replaceButton());

    expect(await screen.findByText(/업로드하지 못했습니다/)).toBeInTheDocument();
    expect(onReplace).not.toHaveBeenCalled();
  });

  it('표지가 아직 없으면 대체 표시를 보여준다', async () => {
    // 표지가 비어 있는 보스가 이 컨트롤의 주 사용처 중 하나다.
    renderControl(vi.fn(), null);

    expect(screen.getByText('표지 없음')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('교체가 진행되는 동안에는 파일을 바꾸지 못한다', async () => {
    const user = userEvent.setup();
    let resolveReplace: (() => void) | undefined;
    const onReplace = vi.fn().mockReturnValue(
      new Promise<void>((resolve) => {
        resolveReplace = resolve;
      }),
    );
    server.use(
      http.post('*/api/admin/book-cover-images', () =>
        HttpResponse.json({ imageAssetId: 777, imageUrl: '/b.webp' }, { status: 201 }),
      ),
    );

    renderControl(onReplace);
    await user.upload(coverInput(), imageFile());
    await user.click(replaceButton());

    // 진행 중에 다른 파일을 고르면, 뒤늦게 도착한 응답이 보존된 자산 ID를 덮어써
    // 화면에 보이는 파일과 다른 이미지가 교체될 수 있다.
    await waitFor(() => expect(coverInput()).toBeDisabled());

    resolveReplace?.();
    await waitFor(() => expect(coverInput()).not.toBeDisabled());
  });

  it('파일을 고르지 않고 누르면 안내만 하고 요청하지 않는다', async () => {
    const user = userEvent.setup();
    const onReplace = vi.fn();

    renderControl(onReplace);
    await user.click(replaceButton());

    expect(await screen.findByText(/교체할 표지 이미지를 선택/)).toBeInTheDocument();
    expect(onReplace).not.toHaveBeenCalled();
  });
});
