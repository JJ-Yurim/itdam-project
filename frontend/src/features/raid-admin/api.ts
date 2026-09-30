import { httpClient } from '@/shared/api';

import type {
  AdminBoss,
  AdminBossCreateRequest,
  AdminBossFilters,
  AdminBossPage,
  AdminBossPhaseImageUpdateRequest,
  AdminBossPhaseTemplatesUpdateRequest,
  AdminBossRun,
  AdminBossRunActionRequest,
  AdminBossRunCreateRequest,
  AdminBossRunFilters,
  AdminBossRunPage,
  AdminBossRunUpdateRequest,
  AdminBossStatusUpdateRequest,
  AdminBossUpdateRequest,
  AdminBook,
  AdminBookCoverResponse,
  AdminBookFilters,
  AdminBookPage,
  AdminBookVisibilityUpdateRequest,
  BossImageUploadResponse,
} from './types';

export async function getAdminBosses({
  query,
  status,
  page = 0,
  size = 20,
}: AdminBossFilters = {}): Promise<AdminBossPage> {
  const { data } = await httpClient.get<AdminBossPage>('/admin/bosses', {
    // 빈 검색어까지 보내면 서버가 빈 문자열로 필터를 걸 수 있어 조건을 아예 생략한다.
    params: { ...(query ? { query } : {}), ...(status ? { status } : {}), page, size },
  });
  return data;
}

/** 새 레이드는 활성 보스로만 만들 수 있으므로 선택지도 활성 보스만 모은다. */
export async function getAllActiveAdminBosses(): Promise<AdminBoss[]> {
  const bosses: AdminBoss[] = [];
  let page = 0;
  let hasNext: boolean;
  do {
    const response = await getAdminBosses({ status: 'ACTIVE', page, size: 100 });
    bosses.push(...response.content);
    hasNext = response.hasNext;
    page += 1;
  } while (hasNext);
  return bosses;
}

export async function createAdminBoss(request: AdminBossCreateRequest): Promise<AdminBoss> {
  const { data } = await httpClient.post<AdminBoss>('/admin/bosses', request);
  return data;
}

export async function updateAdminBoss(
  bossId: number,
  request: AdminBossUpdateRequest,
): Promise<AdminBoss> {
  const { data } = await httpClient.patch<AdminBoss>(`/admin/bosses/${bossId}`, request);
  return data;
}

export async function updateAdminBossPhaseTemplates(
  bossId: number,
  request: AdminBossPhaseTemplatesUpdateRequest,
): Promise<AdminBoss> {
  const { data } = await httpClient.put<AdminBoss>(
    `/admin/bosses/${bossId}/phase-templates`,
    request,
  );
  return data;
}

export async function updateAdminBossStatus(
  bossId: number,
  request: AdminBossStatusUpdateRequest,
): Promise<AdminBoss> {
  const { data } = await httpClient.patch<AdminBoss>(`/admin/bosses/${bossId}/status`, request);
  return data;
}

export async function uploadBossImage(image: File): Promise<BossImageUploadResponse> {
  const formData = new FormData();
  formData.append('image', image);
  const { data } = await httpClient.post<BossImageUploadResponse>('/admin/boss-images', formData);
  return data;
}

export async function uploadBookCoverImage(image: File): Promise<BossImageUploadResponse> {
  const formData = new FormData();
  formData.append('image', image);
  const { data } = await httpClient.post<BossImageUploadResponse>(
    '/admin/book-cover-images',
    formData,
  );
  return data;
}

export async function changeAdminBossBookCover(
  bossId: number,
  imageAssetId: number,
): Promise<AdminBoss> {
  const { data } = await httpClient.put<AdminBoss>(`/admin/bosses/${bossId}/book-cover`, {
    imageAssetId,
  });
  return data;
}

export async function changeAdminBookCover(
  bookId: number,
  imageAssetId: number,
): Promise<AdminBookCoverResponse> {
  const { data } = await httpClient.put<AdminBookCoverResponse>(`/admin/books/${bookId}/cover`, {
    imageAssetId,
  });
  return data;
}

export async function getAdminBossRuns({
  query,
  statuses,
  from,
  to,
  page = 0,
  size = 20,
}: AdminBossRunFilters = {}): Promise<AdminBossRunPage> {
  const { data } = await httpClient.get<AdminBossRunPage>('/admin/boss-runs', {
    // 빈 조건은 파라미터에서 아예 뺀다. 빈 문자열을 보내면 서버가 그 값으로 필터를 건다.
    params: {
      ...(query ? { query } : {}),
      ...(statuses?.length ? { status: statuses } : {}),
      ...(from ? { from } : {}),
      ...(to ? { to } : {}),
      page,
      size,
    },
  });
  return data;
}

export async function createAdminBossRun(
  request: AdminBossRunCreateRequest,
): Promise<AdminBossRun> {
  const { data } = await httpClient.post<AdminBossRun>('/admin/boss-runs', request);
  return data;
}

export async function updateAdminBossRun(
  runId: number,
  request: AdminBossRunUpdateRequest,
): Promise<AdminBossRun> {
  const { data } = await httpClient.patch<AdminBossRun>(`/admin/boss-runs/${runId}`, request);
  return data;
}

/** 예정 회차 즉시 시작·취소, 진행 중 회차 조기 종료. 세 동작 모두 같은 본문을 쓴다. */
export async function runAdminBossRunAction(
  runId: number,
  action: 'activate' | 'cancel' | 'end',
  request: AdminBossRunActionRequest,
): Promise<AdminBossRun> {
  const { data } = await httpClient.post<AdminBossRun>(
    `/admin/boss-runs/${runId}/${action}`,
    request,
  );
  return data;
}

export async function changeAdminBossRunPhaseImage(
  runId: number,
  phaseNo: number,
  request: AdminBossPhaseImageUpdateRequest,
): Promise<AdminBossRun> {
  const { data } = await httpClient.put<AdminBossRun>(
    `/admin/boss-runs/${runId}/phases/${phaseNo}/image`,
    request,
  );
  return data;
}

export async function getAdminBooks({
  query,
  statuses,
  visibility,
  page = 0,
  size = 20,
}: AdminBookFilters = {}): Promise<AdminBookPage> {
  const { data } = await httpClient.get<AdminBookPage>('/admin/books', {
    params: {
      ...(query ? { query } : {}),
      ...(statuses?.length ? { status: statuses } : {}),
      ...(visibility ? { visibility } : {}),
      page,
      size,
    },
  });
  return data;
}

export async function updateAdminBookVisibility(
  bookId: number,
  request: AdminBookVisibilityUpdateRequest,
): Promise<AdminBook> {
  const { data } = await httpClient.patch<AdminBook>(
    `/admin/books/${bookId}/visibility`,
    request,
  );
  return data;
}
