import AxeBuilder from '@axe-core/playwright'
import { type Page, expect, test } from '@playwright/test'
import type { NodeResult, Result } from 'axe-core'
import { expectKeyboardWalk, expectPageRules } from './page-rules'

/**
 * The WCAG 2.2 AA check every page this suite visits gets once its content shows (#143): axe's
 * rules for levels A and AA, failing on a serious or critical violation. A minor or moderate one is
 * left to the keyboard and screen-reader passes. Each page also keeps the rules in `page-rules.ts`,
 * and with `keyboard`, the page is walked by Tab alone: the flows #144 names are.
 */
const wcag22aa = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']
const failingImpacts = new Set(['serious', 'critical'])

/** A library's violation the suite can't fix: its rule, on the element `selector` matches alone. */
interface Suppression {
  rule: string
  selector: string
  reason: string
}

const suppressions: Suppression[] = []

/**
 * Frames that run no script, as the email previews are sandboxed: axe runs inside
 * each frame it checks, so it waits on these until the test times out. What they show is the
 * email's HTML, not the page's, and the tests find each frame by its title.
 */
const scriptlessFrames = 'iframe[sandbox]:not([sandbox~="allow-scripts"])'

/**
 * Checks the page as it now shows against WCAG 2.2 AA, failing on a serious or critical violation
 * with each one's rule, what it asks, and the elements it was found on.
 */
export async function expectAccessible(page: Page, { keyboard = false }: { keyboard?: boolean } = {}): Promise<void> {
  await test.step(`axe: ${new URL(page.url()).pathname}`, async () => {
    const { violations } = await new AxeBuilder({ page }).withTags(wcag22aa).exclude(scriptlessFrames).analyze()
    const failing: Result[] = []
    for (const violation of violations) {
      if (!failingImpacts.has(violation.impact ?? '')) continue
      const nodes = await unsuppressed(page, violation)
      if (nodes.length > 0) failing.push({ ...violation, nodes })
    }
    expect.soft(failing.map(describeViolation), `WCAG 2.2 AA violations on ${page.url()}`).toEqual([])
    await expectPageRules(page)
    if (keyboard) await expectKeyboardWalk(page)
  })
}

async function unsuppressed(page: Page, violation: Result): Promise<NodeResult[]> {
  const selectors = suppressions.filter((s) => s.rule === violation.id).map((s) => s.selector)
  if (selectors.length === 0) return violation.nodes
  const kept: NodeResult[] = []
  for (const node of violation.nodes) {
    const target = node.target.length === 1 ? node.target[0] : undefined
    let suppressed = false
    if (typeof target === 'string') {
      for (const selector of selectors) {
        if ((await page.locator(target).and(page.locator(selector)).count()) > 0) suppressed = true
      }
    }
    if (!suppressed) kept.push(node)
  }
  return kept
}

function describeViolation(violation: Result): string {
  const where = violation.nodes.map((node) => `  ${node.target.join(' ')}: ${node.failureSummary ?? ''}`)
  return [`${violation.id} (${violation.impact}): ${violation.help}`, ...where].join('\n')
}
