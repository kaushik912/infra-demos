import { test, expect } from '@playwright/test';

// Each test gets a fresh browser context + `page` (isolated, no shared state).

test('hello world: page loads', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle('Todo Demo');
  await expect(page.getByRole('heading', { name: 'Todo Demo' })).toBeVisible();
});

// The end-to-end use case: add todos -> complete one -> counter updates.
test('user adds todos and completes one', async ({ page }) => {
  await page.goto('/');

  // Add two todos like a real user: find by label/role, not CSS selectors.
  for (const text of ['Buy milk', 'Learn Playwright']) {
    await page.getByLabel('New todo').fill(text);
    await page.getByRole('button', { name: 'Add' }).click();
  }

  await expect(page.getByRole('listitem')).toHaveCount(2);
  await expect(page.getByText('2 items left')).toBeVisible();

  // Complete one; web-first assertions auto-retry until true or timeout.
  await page.getByLabel('Buy milk').check();
  await expect(page.getByText('1 items left')).toBeVisible();
});

test.fixme('user cannot add an empty todo', async ({ page }) => {
  // TODO(human)
});
