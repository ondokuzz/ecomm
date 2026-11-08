import { type APIRequestContext, type Page, type Route, expect, test } from '@playwright/test'
import { stackSecret } from './secrets'

/**
 * Help, the Support Assistant's panel: opened from every page, it asks a signed-out visitor to sign
 * in; for a Customer it keeps the Conversation across pages and reloads, shows that it is working,
 * links each record a reply names, and ends the Conversation when they sign out; past the hourly
 * message limit, it says when they can ask again. Its waiting and failure states are shown with the
 * assistant's answers stubbed, as the other pages' error states are.
 * Each test signs in as a Customer made for it in Keycloak, and ends its Conversation and deletes the
 * Customer afterwards.
 */

const keycloakUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8180'

interface Customer {
  id: string
  email: string
  password: string
}

let customer: Customer | undefined

test.beforeEach(async ({ request }) => {
  customer = await createCustomer(request)
})

test.afterEach(async ({ page, request }) => {
  if (!customer) return
  const id = await page.evaluate(() => sessionStorage.getItem('ecomm.help.conversationId')).catch(() => null)
  if (id) {
    await request.delete(`/api/support-assistant/conversations/${encodeURIComponent(id)}`, {
      headers: { Authorization: `Bearer ${await tokenFor(request, customer)}` },
    })
  }
  await deleteCustomer(request, customer)
  customer = undefined
})
const conversations = '**/api/support-assistant/conversations'
const conversation = /\/api\/support-assistant\/conversations\/[^/]+$/
const messages = /\/api\/support-assistant\/conversations\/[^/]+\/messages$/

test('a signed-out visitor is asked to sign in', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Ecomm Agent' }).click()

  const help = page.getByRole('dialog', { name: 'Ecomm Agent' })
  await expect(help).toContainText('Sign in to ask about your Orders.')
  await expect(help.getByRole('button', { name: 'Log in' })).toBeFocused()
})

test('focus moves into Help when it opens and back to the button when it closes', async ({ page }) => {
  await signIn(page)
  const toggle = page.getByRole('button', { name: 'Ecomm Agent' })
  await toggle.click()
  const help = page.getByRole('dialog', { name: 'Ecomm Agent' })
  await expect(help.getByRole('textbox', { name: 'Your question' })).toBeFocused()

  await page.keyboard.press('Escape')
  await expect(help).toBeHidden()
  await expect(toggle).toBeFocused()

  await toggle.click()
  await help.getByRole('button', { name: 'Close Ecomm Agent' }).click()
  await expect(help).toBeHidden()
  await expect(toggle).toBeFocused()
})

test('the Conversation carries on across pages and after a reload', async ({ page }) => {
  await signIn(page)
  const help = await openHelp(page)
  await ask(help, 'Hello!')
  await expect(help.getByRole('list', { name: 'Conversation' })).toContainText('I can help with your Orders')

  await page.getByRole('link', { name: 'My Orders' }).first().click()
  await expect(page.getByRole('heading', { name: 'My orders' })).toBeVisible()
  await page.reload()
  const reopened = await openHelp(page)

  await expect(reopened.getByRole('list', { name: 'Conversation' })).toContainText('Hello!')
  await expect(reopened.getByRole('list', { name: 'Conversation' })).toContainText('I can help with your Orders')
})

test('New conversation starts afresh', async ({ page }) => {
  await signIn(page)
  const help = await openHelp(page)
  await ask(help, 'Hello!')
  const list = help.getByRole('list', { name: 'Conversation' })
  await expect(list).toContainText('I can help with your Orders')

  await help.getByRole('button', { name: 'New conversation' }).click()

  await expect(list).not.toContainText('Hello!')
})

test('a Conversation that lapsed is replaced by a new one', async ({ page }) => {
  await signIn(page)
  await page.evaluate(() => sessionStorage.setItem('ecomm.help.conversationId', '00000000-0000-4000-8000-000000000000'))
  const help = await openHelp(page)

  await ask(help, 'Hello!')

  await expect(help.getByRole('list', { name: 'Conversation' })).toContainText('I can help with your Orders')
  expect(await page.evaluate(() => sessionStorage.getItem('ecomm.help.conversationId'))).not.toBe(
    '00000000-0000-4000-8000-000000000000',
  )
})

test('signing out ends the Conversation', async ({ page }) => {
  await signIn(page)
  const help = await openHelp(page)
  await ask(help, 'Hello!')
  await expect(help.getByRole('list', { name: 'Conversation' })).toContainText('I can help with your Orders')
  const id = await page.evaluate(() => sessionStorage.getItem('ecomm.help.conversationId'))

  const ended = page.waitForRequest((r) => r.method() === 'DELETE' && r.url().endsWith(`/conversations/${id}`))
  await page.getByRole('button', { name: 'Close Ecomm Agent' }).click()
  await page.getByRole('button', { name: 'Customer menu' }).click()
  await page.getByRole('button', { name: 'Log out' }).click()

  expect((await (await ended).response())?.status()).toBe(204)
})

test('past the hourly message limit, the Customer is told when they can ask again', async ({ page, request }) => {
  if (!customer) throw new Error('No Customer was made for this test')
  const headers = { Authorization: `Bearer ${await tokenFor(request, customer)}` }
  const started = await request.post('/api/support-assistant/conversations', { headers })
  expect(started.status()).toBe(201)
  const { id } = (await started.json()) as { id: string }
  try {
    // A Conversation holds 20 of the Customer's messages: the hour's whole limit.
    for (let i = 0; i < 20; i++) {
      const sent = await request.post(`/api/support-assistant/conversations/${id}/messages`, {
        headers,
        data: { text: `Hello ${i + 1}` },
      })
      expect(sent.status()).toBe(200)
    }
    await signIn(page)
    const help = await openHelp(page)

    await ask(help, 'Where is my order?')

    await expect(help.getByRole('alert')).toContainText(
      /You've sent a lot of messages\. You can ask again at \d{1,2}:\d{2}/,
    )
    await expect(help.getByRole('textbox', { name: 'Your question' })).toHaveValue('Where is my order?')
  } finally {
    await request.delete(`/api/support-assistant/conversations/${id}`, { headers })
  }
})

test.describe('with the assistant stubbed', () => {
  const reply = {
    role: 'ASSISTANT',
    text: 'Your latest Order, #7F1C2A3B, is paid.',
    references: [
      {
        kind: 'ORDER',
        id: '7f1c2a3b-0000-4000-8000-000000000001',
        reference: '#7F1C2A3B',
        label: 'Order #7F1C2A3B',
      },
    ],
    at: '2026-11-03T10:15:00Z',
  }

  test.beforeEach(async ({ page }) => {
    await page.route(conversations, (route) => route.fulfill({ status: 201, json: { id: 'c-1', messages: [] } }))
    await page.route(conversation, (route) => route.fulfill({ status: 200, json: { id: 'c-1', messages: [] } }))
  })

  test('shows that it is looking things up, then the reply with a link to each record it names', async ({ page }) => {
    let answer: (() => Promise<void>) | undefined
    await page.route(messages, (route) => {
      answer = () => route.fulfill({ status: 200, json: reply })
    })
    await signIn(page)
    const help = await openHelp(page)
    const box = help.getByRole('textbox', { name: 'Your question' })
    await box.fill('Where is my order?')
    await help.getByRole('button', { name: 'Send' }).click()

    await expect(help.getByRole('list', { name: 'Conversation' }).getByText('Looking that up…')).toBeVisible()
    await expect(box).toBeDisabled()
    // Ending the Conversation mid-turn would leave its reply with nowhere to go.
    const newConversation = help.getByRole('button', { name: 'New conversation' })
    await expect(newConversation).toBeDisabled()
    await expect.poll(() => answer !== undefined).toBe(true)
    await answer!()

    await expect(help.getByRole('list', { name: 'Conversation' }).getByText('Looking that up…')).toBeHidden()
    const list = help.getByRole('list', { name: 'Conversation' })
    await expect(list).toContainText('Where is my order?')
    await expect(list).toContainText('Your latest Order, #7F1C2A3B, is paid.')
    await expect(help.locator('[aria-live="polite"]')).toContainText(
      'Ecomm Agent replied: Your latest Order, #7F1C2A3B, is paid.',
    )
    await expect(box).toHaveValue('')
    await expect(box).toBeFocused()
    await expect(newConversation).toBeEnabled()
    await list.getByRole('link', { name: 'Order #7F1C2A3B' }).click()
    await expect(page).toHaveURL(/\/orders\/7f1c2a3b-0000-4000-8000-000000000001$/)
  })

  test('a failed message says so, with its reference, and keeps the text', async ({ page }) => {
    await page.route(messages, (route: Route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/problem+json',
        body: JSON.stringify({ title: 'Internal Server Error', status: 500, correlationId: 'e2e-help-1' }),
      }),
    )
    await signIn(page)
    const help = await openHelp(page)
    await ask(help, 'Where is my order?')

    const alert = help.getByRole('alert').filter({ hasText: "Your message wasn't answered" })
    await expect(alert).toContainText('Reference: e2e-help-1')
    await expect(help.getByRole('textbox', { name: 'Your question' })).toHaveValue('Where is my order?')
  })

  /** The assistant refuses each message with this problem, and `Retry-After` when it is given. */
  async function refuseMessages(page: Page, status: number, reason: string, retryAfter?: number) {
    await page.route(messages, (route: Route) =>
      route.fulfill({
        status,
        contentType: 'application/problem+json',
        headers: retryAfter === undefined ? {} : { 'Retry-After': String(retryAfter) },
        body: JSON.stringify({ status, reason, correlationId: 'e2e-help-2' }),
      }),
    )
  }

  test.describe('in a known locale and time zone', () => {
    test.use({ locale: 'en-GB', timezoneId: 'UTC' })

    test('past the limits, it says at what time the Customer can ask again, and keeps the text', async ({ page }) => {
      await refuseMessages(page, 429, 'assistantRateLimited', 3600)
      await signIn(page)
      const help = await openHelp(page)
      await ask(help, 'Where is my order?')
      // An hour from now, rounded up to the minute.
      const at = new Date(Math.ceil((Date.now() + 3_600_000) / 60_000) * 60_000)
      const later = new Date(at.getTime() + 60_000)
      const time = (d: Date) => `${d.getUTCHours()}:${String(d.getUTCMinutes()).padStart(2, '0')}`

      const alert = help.getByRole('alert')
      await expect(alert).toContainText("You've sent a lot of messages.")
      // The message may have been answered in the next minute.
      await expect(alert).toContainText(new RegExp(`You can ask again at 0?(${time(at)}|${time(later)})\\.`))
      await expect(help.getByRole('textbox', { name: 'Your question' })).toHaveValue('Where is my order?')
    })
  })

  for (const [status, reason] of [
    [503, 'assistantUnavailable'],
    [503, 'assistantBusy'],
  ] as const) {
    test(`${reason} says the assistant is unavailable, with the reference, and keeps the text`, async ({ page }) => {
      await refuseMessages(page, status, reason, reason === 'assistantBusy' ? 30 : undefined)
      await signIn(page)
      const help = await openHelp(page)
      await ask(help, 'Where is my order?')

      const alert = help.getByRole('alert')
      await expect(alert).toContainText('Ecomm Agent is unavailable just now. Please try again later.')
      await expect(alert).toContainText('Reference: e2e-help-2')
      await expect(help.getByRole('textbox', { name: 'Your question' })).toHaveValue('Where is my order?')
    })
  }

  test('a full Conversation offers a new one, which the kept text can be sent to', async ({ page }) => {
    await refuseMessages(page, 409, 'conversationFull')
    await signIn(page)
    const help = await openHelp(page)
    await ask(help, 'Where is my order?')

    const alert = help.getByRole('alert')
    await expect(alert).toContainText('This conversation is full.')
    const box = help.getByRole('textbox', { name: 'Your question' })
    await expect(box).toHaveValue('Where is my order?')

    await page.unroute(messages)
    await page.route(messages, (route) => route.fulfill({ status: 200, json: reply }))
    const startedAgain = page.waitForRequest((r) => r.method() === 'POST' && r.url().endsWith('/conversations'))
    await alert.getByRole('button', { name: 'Start a new conversation' }).click()
    await startedAgain
    await expect(help.getByRole('alert')).toBeHidden()
    await expect(box).toHaveValue('Where is my order?')
    await help.getByRole('button', { name: 'Send' }).click()
    await expect(help.getByRole('list', { name: 'Conversation' })).toContainText(
      'Your latest Order, #7F1C2A3B, is paid.',
    )
  })

  test('a long Conversation scrolls within the panel, which stays on the screen', async ({ page }) => {
    const long = Array.from({ length: 40 }, (_, i) => ({
      ...reply,
      role: i % 2 === 0 ? 'CUSTOMER' : 'ASSISTANT',
      text: `Message ${i + 1}. ${'More words to make it wrap. '.repeat(4)}`,
      references: [],
    }))
    await page.route(conversations, (route) => route.fulfill({ status: 201, json: { id: 'c-1', messages: long } }))
    await page.route(conversation, (route) => route.fulfill({ status: 200, json: { id: 'c-1', messages: long } }))
    await signIn(page)
    const help = await openHelp(page)
    const transcript = help.getByRole('list', { name: 'Conversation' })
    await expect(transcript).toContainText('Message 40.')

    const panel = await help.boundingBox()
    expect(panel!.y + panel!.height).toBeLessThanOrEqual(page.viewportSize()!.height)
    await expect(help.getByRole('textbox', { name: 'Your question' })).toBeInViewport()
    // The latest message shows; the first is scrolled out of the transcript, not off the screen.
    await expect(transcript.getByText('Message 40.')).toBeInViewport()
    expect(await transcript.evaluate((list) => list.scrollHeight > list.clientHeight)).toBe(true)
    const first = transcript.getByText(/^Message 1\. /)
    await expect(first).not.toBeInViewport()
    await first.scrollIntoViewIfNeeded()
    await expect(first).toBeInViewport()
  })

  test('the text box keeps to the 2,000-character limit and counts down', async ({ page }) => {
    await signIn(page)
    const help = await openHelp(page)
    const box = help.getByRole('textbox', { name: 'Your question' })

    await box.fill('x'.repeat(2_100))

    await expect(box).toHaveValue('x'.repeat(2_000))
    await expect(help).toContainText('0 of 2,000 characters left')
  })
})

async function signIn(page: Page) {
  if (!customer) throw new Error('No Customer was made for this test')
  await page.goto('/orders')
  await page.locator('#username').fill(customer.email)
  await page.locator('#password').fill(customer.password)
  await page.locator('#kc-login').click()
  await expect(page.getByRole('heading', { name: 'My orders' })).toBeVisible()
}

async function openHelp(page: Page) {
  await page.getByRole('button', { name: 'Ecomm Agent' }).click()
  const help = page.getByRole('dialog', { name: 'Ecomm Agent' })
  await expect(help.getByRole('textbox', { name: 'Your question' })).toBeEnabled()
  return help
}

async function ask(help: ReturnType<Page['getByRole']>, text: string) {
  await help.getByRole('textbox', { name: 'Your question' }).fill(text)
  await help.getByRole('button', { name: 'Send' }).click()
}

async function keycloakAdminHeaders(request: APIRequestContext) {
  const response = await request.post(`${keycloakUrl}/realms/master/protocol/openid-connect/token`, {
    form: {
      grant_type: 'password',
      client_id: 'admin-cli',
      username: 'admin',
      password: stackSecret('keycloak-admin-password'),
    },
  })
  expect(response.ok()).toBeTruthy()
  return { Authorization: `Bearer ${((await response.json()) as { access_token: string }).access_token}` }
}

/** A new Customer, E2E Customer, ready to sign in. */
async function createCustomer(request: APIRequestContext): Promise<Customer> {
  const email = `e2e-help-${Date.now().toString(36)}${test.info().workerIndex}@ecomm.local`
  const password = 'e2e-password'
  const response = await request.post(`${keycloakUrl}/admin/realms/ecomm/users`, {
    headers: await keycloakAdminHeaders(request),
    data: {
      username: email,
      email,
      firstName: 'E2E',
      lastName: 'Customer',
      enabled: true,
      emailVerified: true,
      credentials: [{ type: 'password', value: password, temporary: false }],
    },
  })
  expect(response.status()).toBe(201)
  return { id: response.headers().location.split('/').pop()!, email, password }
}

async function deleteCustomer(request: APIRequestContext, user: Customer) {
  const response = await request.delete(`${keycloakUrl}/admin/realms/ecomm/users/${user.id}`, {
    headers: await keycloakAdminHeaders(request),
  })
  expect(response.status()).toBe(204)
}

/** A Customer's token straight from Keycloak, through the realm's dev-only password-grant client. */
async function tokenFor(request: APIRequestContext, user: Customer) {
  const response = await request.post(`${keycloakUrl}/realms/ecomm/protocol/openid-connect/token`, {
    form: { grant_type: 'password', client_id: 'dev-cli', username: user.email, password: user.password },
  })
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as { access_token: string }).access_token
}
