# Playwright Demo (hello world)

## Why Playwright is useful
- Drives a real browser (Chromium/Firefox/WebKit) like a user: click, type, assert.
- Auto-waits for elements, web-first assertions retry -> far fewer flaky tests than Selenium-style sleeps.
- Test isolation: fresh browser context per test.
- Built-in: HTML report, traces (time-travel debugging), screenshots/video, codegen, parallel runs, CI-friendly.
- Catches what unit tests can't: wiring between UI, JS, and backend.

## What's here
- `app/index.html` — tiny todo app (no backend, no external site needed)
- `playwright.config.ts` — starts the app via `webServer`, sets `baseURL`
- `tests/todo.spec.ts` — hello-world + one e2e flow (add todos -> complete one -> counter updates)

## Run
```bash
npm install
npx playwright install chromium   # one-time browser download
npm test                          # headless
npm run test:headed               # watch the browser
npm run test:ui                   # interactive UI mode
npm run report                    # open HTML report
npx playwright codegen http://localhost:3000   # record clicks -> test code (start app first: npx http-server app -p 3000)
```
