import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

export default defineConfig(({ mode }) => {
  // 배포된 백엔드에 붙어 테스트할 때만 설정한다. 값이 없으면 프록시 없이 동작한다.
  const proxyTarget = loadEnv(mode, process.cwd(), '').DEV_API_PROXY_TARGET;

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: proxyTarget
      ? {
          proxy: {
            '/api': {
              target: proxyTarget,
              changeOrigin: true,
              // 백엔드는 Origin이 다르면 CORS 요청으로 판정해 403을 낸다.
              // 대상 오리진으로 덮어써 same-origin 요청처럼 보이게 한다.
              headers: { Origin: proxyTarget },
            },
            '/images/boss-phases': {
              target: proxyTarget,
              changeOrigin: true,
            },
            '/images/book-covers': {
              target: proxyTarget,
              changeOrigin: true,
            },
            '/images/system': {
              target: proxyTarget,
              changeOrigin: true,
            },
          },
        }
      : undefined,
    test: {
      environment: 'jsdom',
      setupFiles: './tests/setup.ts',
      // 코어 수만큼 워커를 띄우면 jsdom 환경과 MSW 서버를 파일마다 세우는 비용이 서로
      // 경합한다. 22코어 기준으로 무제한(47초, 매 실행 다른 테스트가 대기 시간을 넘겨
      // 실패)보다 6워커(41초, 안정)가 더 빠르고 결정적이었다. CPU 할당량이 제한된
      // CI 컨테이너에서는 코어 수를 그대로 믿으면 같은 문제가 더 크게 나타난다.
      maxWorkers: 6,
    },
  };
});
