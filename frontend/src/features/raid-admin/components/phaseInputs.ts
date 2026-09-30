export interface PhaseTemplateInput {
  id: string;
  image: File | null;
  /** 업로드에 성공한 자산 ID. 저장만 실패했을 때 같은 파일을 다시 올리지 않기 위해 남긴다. */
  uploadedAssetId: number | null;
  maxHp: string;
  /** 수정 모드에서 지금 걸려 있는 이미지. 생성 모드에서는 없다. */
  currentImageUrl?: string | null;
}

let nextPhaseId = 0;

export const createPhaseInput = (
  values: Partial<PhaseTemplateInput> = {},
): PhaseTemplateInput => ({
  id: `phase-${nextPhaseId++}`,
  image: null,
  uploadedAssetId: null,
  maxHp: '',
  ...values,
});
