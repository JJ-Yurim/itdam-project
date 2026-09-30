import type { RequestHandler } from 'msw';

import { authHandlers } from './auth.handlers';
import { archiveHandlers } from './archive.handlers';
import { bookGenerationHandlers } from './bookGeneration.handlers';
import { bossHandlers } from './boss.handlers';
import { dailyWordsHandlers } from './dailyWords.handlers';
import { feedHandlers } from './feed.handlers';
import { libraryHandlers } from './library.handlers';
import { raidAdminHandlers } from './raidAdmin.handlers';
import { sentencesHandlers } from './sentences.handlers';

export const handlers: RequestHandler[] = [
  ...archiveHandlers,
  ...authHandlers,
  ...bookGenerationHandlers,
  ...bossHandlers,
  ...dailyWordsHandlers,
  ...feedHandlers,
  ...libraryHandlers,
  ...raidAdminHandlers,
  ...sentencesHandlers,
];
