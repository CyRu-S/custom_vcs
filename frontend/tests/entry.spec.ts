import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { register, password } from './helpers'

test('startup, validation, registration, login and logout use real sessions', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('main', { name: 'Starting CustomVCS Studio' })).toBeVisible()
  await page.getByRole('button', { name: 'Skip intro' }).click()
  await expect(page.getByRole('heading', { name: 'Welcome back.' })).toBeVisible()
  await page.goto('/register')
  await page.getByRole('button', { name: 'Create account', exact: true }).click()
  await expect(page.getByLabel('Your name')).toBeFocused()
  const email = await register(page)
  await expect(page.locator('.project-card')).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Good to see you, Arpan.' })).toBeVisible()
  await page.getByRole('button', { name: /Your profile and session/ }).click()
  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel('Email address').fill(email)
  await page.locator('#entry-password').fill('wrong-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Email or password is incorrect.')
  await page.locator('#entry-password').fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Good to see you, Arpan.' })).toBeVisible()
  const values = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))
  expect(values).not.toContain(password)
  await expect(page.locator('body')).not.toContainText('Local demo')
})

test('registration is accessible and reduced motion can skip the intro', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/register')
  await expect(page.getByRole('heading', { name: 'Make room for your ideas.' })).toBeVisible()
  const scan = await new AxeBuilder({ page }).analyze()
  expect(scan.violations).toEqual([])
})
