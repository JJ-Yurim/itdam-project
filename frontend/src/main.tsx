import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import App from '@/App';

import './index.css';

async function enableMocking() {
  const shouldEnableMocking = import.meta.env.DEV && import.meta.env.VITE_ENABLE_MSW === 'true';

  if (!shouldEnableMocking) {
    return;
  }

  const { worker } = await import('@/mocks/browser');

  await worker.start({
    onUnhandledRequest: 'bypass',
  });
}

void enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});
