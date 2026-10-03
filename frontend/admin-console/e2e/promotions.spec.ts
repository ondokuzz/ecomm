import { type APIRequestContext, type Page, expect, test } from '@playwright/test'
import type { Campaign } from '../src/domain/campaign'
import type { Coupon } from '../src/domain/coupon'

/**
 * Staff list, create, edit, switch off and delete Coupons and Campaigns in the Admin Console, and
 * see Promotions' refusals beside the field at fault. Each test's Coupon or Campaign is its own,
 * and is deleted again: through the console when the test gets that far, and through Promotions
 * otherwise.
 */

interface Credentials {
  email: string
  password: string
}

const keycloakUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8180'
const staff: Credentials = { email: 'staff@ecomm.local', password: 'staff' }

let run: string

test.beforeEach(() => {
  run = `${Date.now().toString(36)}${test.info().workerIndex}`.toUpperCase()
})

test.describe('Coupons', () => {
  let code: string

  test.beforeEach(() => {
    code = `E2E-${run}`
  })

  test.afterEach(async ({ request }) => {
    const response = await request.delete(`/api/promotions/coupons/${code}`, { headers: await staffHeaders(request) })
    expect([204, 404]).toContain(response.status())
  })

  test('Staff create, edit, switch off and delete a Coupon', async ({ page, request }) => {
    await page.goto('/promotions/coupons/new')
    await signInOnKeycloak(page, staff)
    await expect(page.getByRole('heading', { name: 'New Coupon' })).toBeVisible()

    await page.getByLabel('Code').fill(code.toLowerCase())
    await page.getByLabel('Fixed amount off').check()
    await page.getByLabel('Amount off', { exact: true }).fill('5')
    await page.getByLabel('Minimum subtotal', { exact: true }).fill('20.00')
    await page.getByLabel('Valid from').fill('2026-01-01T00:00')
    await page.getByLabel('Valid until').fill('2030-01-01T00:00')
    await page.getByRole('button', { name: 'Create Coupon' }).click()

    await expect(page).toHaveURL(/\/promotions\/coupons$/)
    await expect(page.getByRole('status')).toHaveText(`Saved ${code}.`)
    const row = page.getByRole('row').filter({ hasText: code })
    await expect(row).toContainText('off')
    await expect(row).toContainText('On')
    expect(await couponOf(request, code)).toMatchObject({
      code,
      discount: { type: 'AMOUNT_OFF', amountOff: { amountMinor: 500, currency: 'EUR' } },
      minimumSubtotal: { amountMinor: 2000, currency: 'EUR' },
      active: true,
    })

    // Edited, it becomes a percentage with no minimum; the code stays.
    await row.getByRole('link', { name: code, exact: true }).click()
    await expect(page.getByLabel('Amount off', { exact: true })).toHaveValue('5.00')
    await page.getByLabel('Percentage off').check()
    await percentage(page).fill('15')
    await page.getByLabel('Minimum subtotal', { exact: true }).fill('')
    await page.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByRole('status')).toHaveText(`Saved ${code}.`)
    expect(await couponOf(request, code)).toMatchObject({
      discount: { type: 'PERCENT_OFF', percentOff: 15, amountOff: null },
      minimumSubtotal: null,
    })

    await row.getByRole('button', { name: 'Switch off' }).click()
    await expect(page.getByRole('status')).toHaveText(`Switched ${code} off.`)
    await expect(row).toContainText('Off')
    expect((await couponOf(request, code)).active).toBe(false)

    await row.getByRole('button', { name: `Delete ${code}` }).click()
    const dialog = page.getByRole('dialog', { name: `Delete ${code}?` })
    await dialog.getByRole('button', { name: 'Delete Coupon' }).click()
    await expect(page.getByRole('status')).toHaveText(`Deleted ${code}.`)
    await expect(page.getByRole('row').filter({ hasText: code })).toHaveCount(0)
    const gone = await request.get(`/api/promotions/coupons/${code}`, { headers: await staffHeaders(request) })
    expect(gone.status()).toBe(404)
  })

  test('a window that ends before it starts is refused beside Valid until', async ({ page }) => {
    await page.goto('/promotions/coupons/new')
    await signInOnKeycloak(page, staff)

    await page.getByLabel('Code').fill(code)
    await percentage(page).fill('10')
    await page.getByLabel('Valid from').fill('2030-01-01T00:00')
    await page.getByLabel('Valid until').fill('2029-01-01T00:00')
    await page.getByRole('button', { name: 'Create Coupon' }).click()

    const until = page.getByLabel('Valid until')
    await expect(until).toHaveAttribute('aria-invalid', 'true')
    await expect(until).toHaveAccessibleDescription('must be after validFrom')
    await expect(page.getByRole('alert')).toContainText("Couldn't save the Coupon")
  })
})

test.describe('Campaigns', () => {
  let name: string
  // Far from the seeded Audio week's 10, and each test's own.
  let priority: number

  test.beforeEach(() => {
    name = `E2E Campaign ${run}`
    priority = 100_000 + Math.floor(Math.random() * 1_000_000)
  })

  test.afterEach(async ({ request }) => {
    const headers = await staffHeaders(request)
    const response = await request.get('/api/promotions/campaigns', { headers })
    for (const campaign of ((await response.json()) as Campaign[]).filter((c) => c.name.startsWith(name))) {
      const deleted = await request.delete(`/api/promotions/campaigns/${campaign.id}`, { headers })
      expect([204, 404]).toContain(deleted.status())
    }
  })

  test('Staff create, edit, switch off and delete a Campaign, seeing its state', async ({ page, request }) => {
    await page.goto('/promotions/campaigns')
    await signInOnKeycloak(page, staff)
    await expect(page.getByRole('row').filter({ hasText: 'Audio week' })).toContainText('Running')

    await page.getByRole('link', { name: 'New Campaign' }).click()
    await page.getByLabel('Name').fill(name)
    // Audio week has priority 10: Promotions refuses it beside the field.
    await page.getByLabel('Priority').fill('10')
    await page.getByLabel('Audio').check()
    await percentage(page).fill('20')
    await page.getByLabel('Valid from').fill('2026-01-01T00:00')
    await page.getByLabel('Valid until').fill('2030-01-01T00:00')
    await page.getByRole('button', { name: 'Create Campaign' }).click()

    const priorityField = page.getByLabel('Priority')
    await expect(priorityField).toHaveAttribute('aria-invalid', 'true')
    await expect(priorityField).toHaveAccessibleDescription(/is taken/)
    await priorityField.fill(String(priority))
    await expect(priorityField).not.toHaveAttribute('aria-invalid')
    await page.getByRole('button', { name: 'Create Campaign' }).click()

    await expect(page).toHaveURL(/\/promotions\/campaigns$/)
    await expect(page.getByRole('status')).toHaveText(`Saved ${name}.`)
    const row = page.getByRole('row').filter({ hasText: name })
    await expect(row).toContainText('20% off')
    await expect(row).toContainText('Audio')
    await expect(row).toContainText('Running')
    const created = await campaignNamed(request, name)
    expect(created).toMatchObject({
      discount: { type: 'PERCENT_OFF', percentOff: 20 },
      categories: ['audio'],
      priority,
      validFrom: new Date('2026-01-01T00:00').toISOString().replace('.000Z', 'Z'),
      state: 'running',
    })

    // Moved into the future, it is scheduled.
    await row.getByRole('link', { name, exact: true }).click()
    await page.getByLabel('Name').fill(`${name} later`)
    await page.getByLabel('Valid from').fill('2029-01-01T00:00')
    await page.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByRole('status')).toHaveText(`Saved ${name} later.`)
    const later = page.getByRole('row').filter({ hasText: `${name} later` })
    await expect(later).toContainText('Scheduled')

    await later.getByRole('button', { name: 'Switch off' }).click()
    await expect(later).toContainText('Off')
    expect((await campaignNamed(request, `${name} later`)).state).toBe('off')

    await later.getByRole('button', { name: `Delete ${name} later` }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Delete Campaign' }).click()
    await expect(page.getByRole('status')).toHaveText(`Deleted ${name} later.`)
    await expect(page.getByRole('row').filter({ hasText: name })).toHaveCount(0)
    const gone = await request.get(`/api/promotions/campaigns/${created.id}`, { headers: await staffHeaders(request) })
    expect(gone.status()).toBe(404)
  })
})

/** The percentage field, whose label the "Percentage off" choice shares a word with. */
function percentage(page: Page) {
  return page.getByRole('textbox', { name: /^Percentage/ })
}

async function signInOnKeycloak(page: Page, user: Credentials) {
  await page.locator('#username').fill(user.email)
  await page.locator('#password').fill(user.password)
  await page.locator('#kc-login').click()
}

/** A Staff token straight from Keycloak, through the realm's dev-only password-grant client. */
async function staffHeaders(request: APIRequestContext) {
  const response = await request.post(`${keycloakUrl}/realms/ecomm/protocol/openid-connect/token`, {
    form: { grant_type: 'password', client_id: 'dev-cli', username: staff.email, password: staff.password },
  })
  expect(response.ok()).toBeTruthy()
  return { Authorization: `Bearer ${((await response.json()) as { access_token: string }).access_token}` }
}

async function couponOf(request: APIRequestContext, code: string): Promise<Coupon> {
  const response = await request.get(`/api/promotions/coupons/${code}`, { headers: await staffHeaders(request) })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as Coupon
}

async function campaignNamed(request: APIRequestContext, name: string): Promise<Campaign> {
  const response = await request.get('/api/promotions/campaigns', { headers: await staffHeaders(request) })
  expect(response.ok()).toBeTruthy()
  const campaign = ((await response.json()) as Campaign[]).find((c) => c.name === name)
  expect(campaign).toBeDefined()
  return campaign!
}
