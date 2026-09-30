export const bookGenerationQueryKeys = {
  all: ['bookGeneration'] as const,

  detail: (bossRunId: number) => [...bookGenerationQueryKeys.all, bossRunId] as const,
};
