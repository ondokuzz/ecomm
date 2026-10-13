import { expect, test } from '@playwright/test'

/** What a seeded Product's page tells a Customer before they buy. Nothing here changes data. */

test("a Product page shows the Product's Warranty terms", async ({ page }) => {
  await page.goto('/products/PHN-GALAXY-S24')
  await expect(page.getByRole('heading', { name: 'Samsung Galaxy S24' })).toBeVisible()
  await expect(page.getByText("24 months, manufacturer's warranty (Samsung)")).toBeVisible()

  await page.goto('/products/AUD-JBL-FLIP-6')
  await expect(page.getByText("12 months, manufacturer's warranty (JBL)")).toBeVisible()
})
