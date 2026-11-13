import { expect, type Page } from '@playwright/test'
export const password = 'Learning-test-42'
export async function register(page: Page) {
  await page.addInitScript(() => sessionStorage.setItem('customvcs.intro.v1', 'active'))
  await page.goto('/register')
  const email = `student-${Date.now()}-${Math.random().toString(16).slice(2)}@example.test`
  await page.getByLabel('Your name').fill('Arpan Test')
  await page.getByLabel('Email address').fill(email)
  await page.locator('#entry-password').fill(password)
  await page.locator('#entry-confirmation').fill(password)
  await page.getByRole('button', { name: 'Create account', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByRole('heading', { name: 'Good to see you, Arpan.' })).toBeVisible()
  return email
}
export async function request(page: Page, path: string, method = 'GET', data?: unknown) {
  const headers: Record<string, string> = {}
  if (method !== 'GET') {
    const response = await page.request.get('/api/auth/csrf')
    const csrf = await response.json()
    headers[csrf.headerName] = csrf.token
  }
  const response = await page.request.fetch('/api' + path, { method, headers, data })
  expect(response.ok(), await response.text()).toBeTruthy()
  return response.status() === 204 ? null : response.json()
}
export async function createRepository(page: Page, name = 'connected-project') {
  await page.getByRole('button', { name: 'New repository', exact: true }).first().click()
  await page.getByLabel('Repository name', { exact: true }).fill(name)
  await page.getByLabel('Description optional').fill('A real backend repository.')
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Create repository', exact: true })
    .click()
  await expect(page.locator('.studio-identity h1')).toContainText(name)
  return /\/r\/([^/]+)/.exec(page.url())![1]
}
export async function seedCommit(page: Page, id: string, content: string, message: string) {
  await request(page, `/repositories/${id}/files/content`, 'PUT', { path: 'hello.txt', content })
  await request(page, `/repositories/${id}/stage`, 'POST', { path: 'hello.txt' })
  return request(page, `/repositories/${id}/commits`, 'POST', {
    author: 'Ignored impostor',
    message,
  })
}
