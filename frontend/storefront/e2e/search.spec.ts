import { type APIRequestContext, type Page, expect, test } from '@playwright/test'
import type { ProductSummary, SearchResults } from '../src/domain/search'

/**
 * The listing is Search's: Customers search, filter by Category and attributes with disjunctive
 * counts, keep to what is in stock, sort and page, and the URL holds all of it, so a reload or a
 * shared link restores the same view. Run against the seed's Products.
 */

test('a Customer searches, filters and sorts, and a reload or a shared URL restores the view', async ({ page, request }) => {
  await page.goto('/')
  await page.getByRole('searchbox', { name: 'Search products' }).fill('pixel')
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page).toHaveURL(/\/\?q=pixel$/)
  await expect(page.getByRole('heading', { name: 'Results for “pixel”' })).toBeVisible()
  await expect(cards(page).locator('.product-name').getByText('Google Pixel 9', { exact: true })).toBeVisible()

  // Clearing the text and choosing a Category offers that Category's attributes.
  await page.getByRole('searchbox', { name: 'Search products' }).fill('')
  await page.getByRole('button', { name: 'Search' }).click()
  await page.getByRole('navigation', { name: 'Categories' }).getByRole('link', { name: /^Phones/ }).click()
  await expect(page).toHaveURL(/\/\?category=phones$/)
  const storage = page.getByRole('group', { name: 'storage' })
  // Ticking searches; the box shows ticked once the URL, which holds the choice, has changed.
  await storage.getByRole('checkbox', { name: /^256 GB/ }).click()
  await expect(storage.getByRole('checkbox', { name: /^256 GB/ })).toBeChecked()
  await expect(page).toHaveURL(/category=phones&attr\.storage=256\+GB/)
  const narrowed = await searchOf(request, 'category=phones&attr.storage=256+GB')
  await expect(cards(page)).toHaveCount(narrowed.items.length)

  // Counts are disjunctive: 128 GB is still offered beside the chosen 256 GB, with its own count.
  const all = await searchOf(request, 'category=phones')
  const count128 = all.facets.attributes.find((a) => a.name === 'storage')!.values.find((v) => v.value === '128 GB')!.count
  await expect(storage.getByRole('checkbox', { name: /^128 GB/ })).toBeEnabled()
  await expect(storage.locator('label').filter({ hasText: '128 GB' })).toContainText(String(count128))

  await page.getByRole('checkbox', { name: /^In stock only/ }).click()
  await expect(page.getByRole('checkbox', { name: /^In stock only/ })).toBeChecked()
  await page.getByLabel('Sort by').selectOption({ label: 'Price: high to low' })
  const query = 'category=phones&attr.storage=256+GB&inStock=true&sort=price-desc'
  await expect(page).toHaveURL(new RegExp(`/\\?${escape(query)}$`))
  const sorted = await searchOf(request, query)
  await expectCardsInOrder(page, sorted.items)

  await page.reload()
  await expect(page.getByRole('group', { name: 'storage' }).getByRole('checkbox', { name: /^256 GB/ })).toBeChecked()
  await expect(page.getByRole('checkbox', { name: /^In stock only/ })).toBeChecked()
  await expect(page.getByLabel('Sort by')).toHaveValue('price-desc')
  await expectCardsInOrder(page, sorted.items)

  const shared = await page.context().newPage()
  await shared.goto(`/?${query}`)
  await expectCardsInOrder(shared, sorted.items)
  await shared.close()
})

test('a search that matches nothing offers to clear the filters', async ({ page }) => {
  await page.goto('/?category=phones&attr.storage=1+TB&attr.brand=Nobody')
  await expect(page.getByRole('heading', { name: 'No products match' })).toBeVisible()
  await page.getByRole('link', { name: 'Clear filters' }).last().click()
  await expect(page).toHaveURL(/\/$/)
  await expect(cards(page).first()).toBeVisible()
})

test('results come a page at a time, and a Product with no Stock is marked', async ({ page }) => {
  // The seed has fewer Products than a page holds, so Search's answer is stood in for.
  const requested: string[] = []
  await page.route(/\/api\/search-discovery\/search\?/, (route) => {
    const index = Number(new URL(route.request().url()).searchParams.get('page'))
    requested.push(route.request().url())
    return route.fulfill({ json: aPageOfResults(index) })
  })

  await page.goto('/')
  await expect(page.getByText('Page 1 of 2')).toBeVisible()
  await expect(card(page, 'Product 1').getByText('Out of stock')).toBeVisible()
  await expect(card(page, 'Product 2').getByText('Out of stock')).toHaveCount(0)

  await page.getByRole('link', { name: 'Next page' }).click()
  await expect(page).toHaveURL(/\/\?page=2$/)
  await expect(page.getByText('Page 2 of 2')).toBeVisible()
  await expect(cards(page).locator('.product-name').getByText('Product 25', { exact: true })).toBeVisible()
  expect(new URL(requested.at(-1)!).searchParams.get('page')).toBe('1')

  await page.reload()
  await expect(page.getByText('Page 2 of 2')).toBeVisible()
  await page.getByRole('link', { name: 'Previous page' }).click()
  await expect(page).toHaveURL(/\/$/)
})

function cards(page: Page) {
  return page.getByRole('list', { name: 'Products' }).getByRole('listitem')
}

/** The card of the Product named exactly `name`. */
function card(page: Page, name: string) {
  return cards(page).filter({ has: page.locator('.product-name').getByText(name, { exact: true }) })
}

async function expectCardsInOrder(page: Page, items: ProductSummary[]) {
  await expect(cards(page).locator('.product-name')).toHaveText(items.map((item) => item.name))
}

async function searchOf(request: APIRequestContext, query: string): Promise<SearchResults> {
  const response = await request.get(`/api/search-discovery/search?${query}`)
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as SearchResults
}

/** 30 made-up Products, 24 to a page; the first has no Stock. */
function aPageOfResults(page: number): SearchResults {
  const all = Array.from({ length: 30 }, (_, i): ProductSummary => ({
    sku: `E2E-${i + 1}`,
    name: `Product ${i + 1}`,
    image: null,
    priceFrom: { amountMinor: 1000, currency: 'EUR' },
    priceVaries: false,
    inStock: i !== 0,
    attributes: {},
  }))
  return {
    items: all.slice(page * 24, page * 24 + 24),
    page,
    size: 24,
    total: all.length,
    facets: { categories: [], attributes: [], prices: [], inStock: 29 },
  }
}

function escape(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
