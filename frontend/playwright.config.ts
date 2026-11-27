import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.pw.ts',
  workers: 2,
  use: { baseURL: 'http://127.0.0.1:5420', trace: 'retain-on-failure' },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium', channel: process.env.PW_CHROME_CHANNEL || undefined }
    },
    { name: 'firefox', use: { browserName: 'firefox' } },
    { name: 'webkit', use: { browserName: 'webkit' } }
  ],
  webServer: {
    command: 'npm run preview -- --host 127.0.0.1 --port 5420 --strictPort',
    url: 'http://127.0.0.1:5420',
    reuseExistingServer: !process.env.CI
  }
});
