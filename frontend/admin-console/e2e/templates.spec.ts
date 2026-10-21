import { expect, test } from '@playwright/test'
import { signInOnKeycloak } from './stack'

/**
 * Staff preview the Message templates: each Message type on each Notification channel, rendered by
 * Notifications with its sample content for a made-up Customer.
 */
test('Staff preview the Order confirmation email on the Message templates page', async ({ page }) => {
  await page.goto('/')
  await signInOnKeycloak(page)
  await page.getByRole('navigation', { name: 'Sections' }).getByRole('link', { name: 'Messages' }).click()

  await expect(page).toHaveURL(/\/message-templates$/)
  await expect(page.getByRole('heading', { name: 'Message templates', level: 1 })).toBeVisible()
  const confirmed = page.getByRole('region', { name: 'Order confirmed' })
  await expect(confirmed).toContainText('Email')
  await expect(confirmed).toContainText('Subject: Your Order #3F2A9C1B is confirmed')
  const html = page.frameLocator('iframe[title="Order confirmed: Email as HTML"]')
  await expect(html.getByText('Hello Ada,')).toBeVisible()
  await expect(html.getByRole('cell', { name: 'Google Pixel 9 (128 GB, Obsidian)' })).toBeVisible()

  await confirmed.getByText('Plain text').click()
  await expect(confirmed.locator('pre')).toContainText('Total: €1,406.29')
})
