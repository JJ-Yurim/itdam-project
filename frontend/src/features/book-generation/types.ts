export type BookGenerationStatus = 'GENERATING' | 'COMPLETED' | 'FAILED';

export type AiJobStatus = 'PENDING' | 'PROCESSING' | 'RETRYING' | 'SUCCESS' | 'FAILED';

export interface BookGenerationStatusResponse {
  bossRunId: number;
  bookId: number;
  bookStatus: BookGenerationStatus;
  jobId: number;
  jobStatus: AiJobStatus;
  requestedAt: string;
  completedAt: string | null;
  updatedAt: string;
}
