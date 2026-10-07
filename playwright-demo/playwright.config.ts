import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  // Playwright starts the app for us, waits for the URL, then runs tests.
  webServer: {
    command: 'npx http-server app -p 3000 -c-1 --silent',
    url: 'http://localhost:3000',
    reuseExistingServer: true,
  },
});
