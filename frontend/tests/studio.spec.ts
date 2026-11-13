import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { register, request, createRepository, seedCommit, password } from './helpers'

test.beforeEach(async ({ page }) => {
  await register(page)
})

test('create, edit, stage, commit, history, reload, and remove a real repository', async ({
  page,
}) => {
  const id = await createRepository(page)
  await page.getByRole('button', { name: 'New file', exact: true }).click()
  await page.getByLabel('File path').fill('hello.txt')
  await page.getByRole('dialog').getByRole('button', { name: 'Create file', exact: true }).click()
  await page.getByRole('button', { name: 'Edit file', exact: true }).click()
  await page.getByLabel('Edit file contents').fill('Hello from the real UI!\n')
  await page.getByRole('button', { name: 'Save changes', exact: true }).click()
  await page
    .getByRole('navigation', { name: 'Repository sections' })
    .getByRole('link', { name: /^Changes/ })
    .click()
  await page.getByRole('button', { name: 'Stage hello.txt', exact: true }).click()
  await page.getByLabel('Commit message').fill('The first real chapter')
  await page.getByRole('button', { name: 'Commit staged changes', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Everything is committed' })).toBeVisible()
  await page
    .getByRole('navigation', { name: 'Repository sections' })
    .getByRole('link', { name: 'History', exact: true })
    .click()
  await expect(page.locator('.commit-detail-heading h3')).toHaveText('The first real chapter')
  await expect(page.locator('.diff-viewer')).toContainText('Hello from the real UI!')
  const history = await request(page, `/repositories/${id}/commits`)
  expect(history[0].id).toMatch(/^[a-f0-9]{64}$/)
  expect(history[0].commit.author).toBe('Arpan Test')
  await page.reload()
  await expect(page.locator('.commit-detail-heading h3')).toHaveText('The first real chapter')
  await page.screenshot({
    path: 'test-results/visuals/connected-history.png',
    fullPage: true,
    animations: 'disabled',
  })
  const scan = await new AxeBuilder({ page }).analyze()
  expect(scan.violations).toEqual([])
  await page
    .getByRole('navigation', { name: 'Repository sections' })
    .getByRole('link', { name: 'Settings', exact: true })
    .click()
  await page.getByRole('button', { name: 'Remove repository', exact: true }).click()
  await page.getByLabel('Type connected-project to confirm').fill('connected-project')
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Remove repository', exact: true })
    .click()
  await expect(page).toHaveURL(/\/repositories$/)
  expect((await request(page, '/repositories')).totalElements).toBe(0)
})

test('branch checkout, text conflict resolution, and a two-parent merge are real engine operations', async ({
  page,
}) => {
  const id = await createRepository(page, 'merge-project')
  await seedCommit(page, id, 'base\n', 'Base')
  await request(page, `/repositories/${id}/branches`, 'POST', { name: 'feature' })
  await seedCommit(page, id, 'ours\n', 'Main change')
  await request(page, `/repositories/${id}/checkout`, 'POST', { name: 'feature' })
  await seedCommit(page, id, 'theirs\n', 'Feature change')
  await request(page, `/repositories/${id}/checkout`, 'POST', { name: 'main' })
  await page.goto(`/r/${id}/merge`)
  await page.getByLabel('Source branch').selectOption('feature')
  await page.getByRole('button', { name: 'Merge branches', exact: true }).click()
  await expect(page.getByText('1 unresolved conflicts')).toBeVisible()
  await page.screenshot({
    path: 'test-results/visuals/connected-merge.png',
    fullPage: true,
    animations: 'disabled',
  })
  await page.getByRole('button', { name: 'Use current', exact: true }).click()
  await page.getByRole('button', { name: 'Save and stage resolution', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'All resolutions are staged.' })).toBeVisible()
  await page.getByRole('button', { name: 'Complete merge commit', exact: true }).click()
  const history = await request(page, `/repositories/${id}/commits`)
  expect(history[0].commit.secondParentId).toMatch(/^[a-f0-9]{64}$/)
  expect((await request(page, `/repositories/${id}/merge`)).inProgress).toBe(false)
  await page.goto(`/r/${id}/branches`)
  await page.getByRole('button', { name: 'Delete branch feature', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Delete branch', exact: true }).click()
  await expect(
    page.getByRole('button', { name: 'Delete branch feature', exact: true }),
  ).toHaveCount(0)
})

test('narrow screens, theme, search, empty state and API failure recovery', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: 'Switch to dark theme' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.getByRole('button', { name: 'Create repository', exact: true }).first().click()
  await page.getByLabel('Repository name', { exact: true }).fill('mobile-project')
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Create repository', exact: true })
    .click()
  await expect(page.locator('.studio-identity h1')).toContainText('mobile-project')
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
  ).toBeTruthy()
  await page.screenshot({
    path: 'test-results/visuals/connected-mobile.png',
    fullPage: true,
    animations: 'disabled',
  })
  await page.goto('/repositories')
  await page.route('**/api/repositories?page=0&size=20', (route) => route.abort())
  await page.reload()
  await expect(page.getByRole('alert')).toContainText('Cannot reach the server')
  await page.unroute('**/api/repositories?page=0&size=20')
  await page.getByRole('button', { name: 'Refresh', exact: true }).click()
  await expect(page.locator('.project-card')).toHaveCount(1)
})

test('unsaved source asks before leaving and survives session expiry in memory', async ({
  page,
}) => {
  const me = await request(page, '/auth/me')
  const id = await createRepository(page, 'draft-project')
  await seedCommit(page, id, 'saved\n', 'Base')
  await page.goto(`/r/${id}/code?file=hello.txt`)
  await page.getByRole('button', { name: 'Edit file', exact: true }).click()
  await page.getByLabel('Edit file contents').fill('unfinished draft\n')
  await page
    .getByRole('navigation', { name: 'Repository sections' })
    .getByRole('link', { name: 'History', exact: true })
    .click()
  await expect(page.getByRole('dialog')).toContainText('Leave unsaved changes?')
  await page.getByRole('button', { name: 'Keep editing', exact: true }).click()
  await expect(page.getByLabel('Edit file contents')).toHaveValue('unfinished draft\n')
  // Simulate another tab ending the session, without reloading this tab's unsaved editor.
  await request(page, '/auth/logout', 'POST')
  await page.getByRole('button', { name: 'Refresh', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel('Email address').fill(me.email)
  await page.locator('#entry-password').fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByLabel('Edit file contents')).toHaveValue('unfinished draft\n')
  await page
    .getByRole('navigation', { name: 'Repository sections' })
    .getByRole('link', { name: 'History', exact: true })
    .click()
  await page.getByRole('button', { name: 'Discard edits', exact: true }).click()
  await expect(page).toHaveURL(/\/history$/)
  const content = await request(page, `/repositories/${id}/files/content?path=hello.txt`)
  expect(content.content).toBe('saved\n')
})
