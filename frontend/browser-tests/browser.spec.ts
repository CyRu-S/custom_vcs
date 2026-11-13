import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { readFile } from 'node:fs/promises'
import { createRepository } from '../tests/helpers'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('customvcs.intro.v1', 'active'))
  await page.goto('/')
  await expect(page.getByText('Browser storage', { exact: true })).toBeVisible()
})

test('create, edit, stage, commit, history, reload and remove without an API server', async ({
  page,
}) => {
  const apiRequests: string[] = []
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/api/')) apiRequests.push(request.url())
  })
  await createRepository(page, 'browser-project')
  await page.getByRole('button', { name: 'New file', exact: true }).click()
  await page.getByLabel('File path').fill('hello.txt')
  await page.getByRole('dialog').getByRole('button', { name: 'Create file', exact: true }).click()
  await page.getByRole('button', { name: 'Edit file', exact: true }).click()
  await page.getByLabel('Edit file contents').fill('Saved entirely in my browser!\n')
  await page.getByRole('button', { name: 'Save changes', exact: true }).click()
  const sections = page.getByRole('navigation', { name: 'Repository sections' })
  await sections.getByRole('link', { name: /^Changes/ }).click()
  await page.getByRole('button', { name: 'Stage hello.txt', exact: true }).click()
  await page.getByLabel('Commit message').fill('First browser commit')
  await page.getByRole('button', { name: 'Commit staged changes', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Everything is committed' })).toBeVisible()
  await sections.getByRole('link', { name: 'History', exact: true }).click()
  await expect(page.locator('.commit-detail-heading h3')).toHaveText('First browser commit')
  await expect(page.locator('.diff-viewer')).toContainText('Saved entirely in my browser!')
  await page.reload()
  await expect(page.locator('.commit-detail-heading h3')).toHaveText('First browser commit')
  await page.screenshot({
    path: 'test-results/browser-desktop.png',
    fullPage: true,
    animations: 'disabled',
  })
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
  await sections.getByRole('link', { name: 'Settings', exact: true }).click()
  await page.getByRole('button', { name: 'Remove repository', exact: true }).click()
  await page.getByLabel('Type browser-project to confirm').fill('browser-project')
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Remove repository', exact: true })
    .click()
  await expect(page).toHaveURL(/\/repositories$/)
  expect(apiRequests).toEqual([])
})

test('branches retain snapshots, dirty checkout is refused and conflicts can be resolved', async ({
  page,
}) => {
  const id = await page.evaluate(async () => {
    const { browserApi: api } = await import('/src/browser-api.ts')
    const r = await api<{ id: string }>('/repositories', {
      method: 'POST',
      body: { name: 'merge-browser' },
    })
    const route = '/repositories/' + r.id
    const seed = async (content: string, message: string) => {
      await api(route + '/files/content', { method: 'PUT', body: { path: 'hello.txt', content } })
      await api(route + '/stage', { method: 'POST', body: { path: 'hello.txt' } })
      await api(route + '/commits', { method: 'POST', body: { message } })
    }
    await seed('base\n', 'Base')
    await api(route + '/branches', { method: 'POST', body: { name: 'feature' } })
    await seed('ours\n', 'Main change')
    await api(route + '/checkout', { method: 'POST', body: { name: 'feature' } })
    await seed('theirs\n', 'Feature change')
    await api(route + '/checkout', { method: 'POST', body: { name: 'main' } })
    return r.id
  })
  await page.goto(`/r/${id}/merge`)
  await page.getByLabel('Source branch').selectOption('feature')
  await page.getByRole('button', { name: 'Merge branches', exact: true }).click()
  await expect(page.getByText('1 unresolved conflicts')).toBeVisible()
  await page.getByRole('button', { name: 'Use incoming', exact: true }).click()
  await page.getByRole('button', { name: 'Save and stage resolution', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'All resolutions are staged.' })).toBeVisible()
  await page
    .getByRole('navigation', { name: 'Repository sections' })
    .getByRole('link', { name: /^Changes/ })
    .click()
  await page.getByLabel('Commit message').fill('Resolved in the browser')
  await page.getByRole('button', { name: 'Complete merge commit', exact: true }).click()
  const outcome = await page.evaluate(async (id) => {
    const { browserApi: api } = await import('/src/browser-api.ts')
    const route = '/repositories/' + id
    const r = await api<{ commits: { parents: string[] }[]; files: Record<string, string> }>(
      route + '/workspace',
    )
    await api(route + '/files/content', {
      method: 'PUT',
      body: { path: 'hello.txt', content: 'pending' },
    })
    let error = ''
    try {
      await api(route + '/checkout', { method: 'POST', body: { name: 'feature' } })
    } catch (failure) {
      error = (failure as Error).message
    }
    return { parents: r.commits[0].parents.length, content: r.files['hello.txt'], error }
  }, id)
  expect(outcome.parents).toBe(2)
  expect(outcome.content).toBe('theirs\n')
  expect(outcome.error).toContain('Commit your changes')
})

test('backup export and import preserve files and reject overwriting existing IDs', async ({
  page,
}) => {
  const id = await createRepository(page, 'backup-project')
  await page.evaluate(async (id) => {
    const { browserApi: api } = await import('/src/browser-api.ts')
    const route = '/repositories/' + id
    await api(route + '/files/content', {
      method: 'PUT',
      body: { path: 'saved.txt', content: 'backed up content' },
    })
    await api(route + '/stage', { method: 'POST', body: { path: 'saved.txt' } })
    await api(route + '/commits', { method: 'POST', body: { message: 'Backup snapshot' } })
    await api(route + '/files/content', {
      method: 'PUT',
      body: { path: 'saved.txt', content: 'staged content' },
    })
    await api(route + '/stage', { method: 'POST', body: { path: 'saved.txt' } })
  }, id)
  await page.goto('/settings')
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Export backup', exact: true }).click()
  const downloaded = await downloadPromise
  const text = await readFile((await downloaded.path())!, 'utf8')
  const backup = JSON.parse(text)
  expect(backup.repositories[0].name).toBe('backup-project')
  expect(backup.repositories[0].commits[0].files['saved.txt']).toBe('backed up content')
  expect(backup.repositories[0].index['saved.txt']).toBe('staged content')
  const input = page.getByLabel('Import browser workspace backup')
  await input.setInputFiles({
    name: 'backup.json',
    mimeType: 'application/json',
    buffer: Buffer.from(text),
  })
  await expect(page.getByRole('alert')).toContainText('already in your browser')
  await page.evaluate(() => localStorage.removeItem('customvcs.browser.v1'))
  await page.reload()
  await input.setInputFiles({
    name: 'backup.json',
    mimeType: 'application/json',
    buffer: Buffer.from(text),
  })
  await page.goto('/repositories')
  await expect(page.getByRole('link', { name: 'backup-project', exact: true })).toBeVisible()
  const restored = await page.evaluate(async (id) => {
    const { browserApi: api } = await import('/src/browser-api.ts')
    return api<{ changes: { path: string; staged: boolean }[]; files: Record<string, string> }>(
      '/repositories/' + id + '/workspace',
    )
  }, id)
  expect(restored.files['saved.txt']).toBe('staged content')
  expect(restored.changes.some((change) => change.path === 'saved.txt' && change.staged)).toBe(true)
})

test('quota failures preserve saved data and invalid paths are rejected', async ({ page }) => {
  const result = await page.evaluate(async () => {
    const { browserApi: api, BROWSER_KEY } = await import('/src/browser-api.ts')
    const r = await api<{ id: string }>('/repositories', {
      method: 'POST',
      body: { name: 'safe-browser' },
    })
    const before = localStorage.getItem(BROWSER_KEY)
    const setter = Storage.prototype.setItem
    Storage.prototype.setItem = function (key, value) {
      if (key === BROWSER_KEY) throw new DOMException('Quota exceeded', 'QuotaExceededError')
      setter.call(this, key, value)
    }
    let error = '',
      pathError = ''
    try {
      await api('/repositories/' + r.id + '/files/content', {
        method: 'PUT',
        body: { path: 'hello.txt', content: 'do not lose data' },
      })
    } catch (failure) {
      error = (failure as Error).message
    } finally {
      Storage.prototype.setItem = setter
    }
    try {
      await api('/repositories/' + r.id + '/files/content', {
        method: 'PUT',
        body: { path: '../outside', content: '' },
      })
    } catch (failure) {
      pathError = (failure as Error).message
    }
    return { same: before === localStorage.getItem(BROWSER_KEY), error, pathError }
  })
  expect(result.same).toBe(true)
  expect(result.error).toContain('Browser storage is full')
  expect(result.pathError).toContain('relative file path')
})

test('mobile workspace and keyboard navigation work without a login', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('heading', { name: /Good to see you/ })).toBeVisible()
  await page.keyboard.press('Control+k')
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).not.toBeVisible()
  await page.screenshot({
    path: 'test-results/browser-mobile.png',
    fullPage: true,
    animations: 'disabled',
  })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
})

test('corrupt storage produces a recoverable error without overwriting the data', async ({
  page,
}) => {
  await page.evaluate(() => localStorage.setItem('customvcs.browser.v1', '{broken'))
  await page.reload()
  await expect(
    page.getByRole('heading', { name: 'Browser storage could not be opened.' }),
  ).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('customvcs.browser.v1'))).toBe('{broken')
})
