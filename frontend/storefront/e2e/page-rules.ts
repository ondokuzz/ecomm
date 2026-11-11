import AxeBuilder from '@axe-core/playwright'
import { type Page, expect } from '@playwright/test'

/**
 * The rules every page keeps (#144) that axe's WCAG tags leave out or can't see: one `h1`, one
 * `main`, all content inside a landmark, each invalid control tied to its message by
 * `aria-describedby`, no horizontal scrolling at 320 px wide or at 200% zoom (640 px of a
 * 1280 px window), and no animation still running for a Customer or Staff member who asks for
 * reduced motion. Each broken rule is a soft failure naming what broke it.
 */
export async function expectPageRules(page: Page): Promise<void> {
  const where = page.url()
  expect.soft(await structureProblems(page), `page rules on ${where}`).toEqual([])
  const { violations } = await new AxeBuilder({ page })
    .withRules(['region', 'landmark-one-main', 'landmark-unique', 'landmark-no-duplicate-banner'])
    .exclude('iframe[sandbox]:not([sandbox~="allow-scripts"])')
    .analyze()
  expect
    .soft(
      violations.map((v) => `${v.id}: ${v.help}\n${v.nodes.map((n) => `  ${n.target.join(' ')}`).join('\n')}`),
      `landmark rules on ${where}`,
    )
    .toEqual([])
  expect.soft(await reflowProblems(page), `reflow on ${where}`).toEqual([])
  expect.soft(await motionProblems(page), `motion under prefers-reduced-motion on ${where}`).toEqual([])
}

/**
 * WCAG 2.3.3: asked for reduced motion, the page's animations stop at once; a spinner or skeleton
 * shimmer still turning is named. The test's own choice of motion is put back after.
 */
async function motionProblems(page: Page): Promise<string[]> {
  const reduced = await page.evaluate(() => matchMedia('(prefers-reduced-motion: reduce)').matches)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  try {
    await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 100)))
    return await page.evaluate(() =>
      document
        .getAnimations()
        // Animations, not transitions: a colour easing in as focus moved, begun before the switch,
        // is no motion, and new transitions take 1 ms under the rule.
        .filter(
          (a): a is CSSAnimation =>
            a instanceof CSSAnimation &&
            a.playState === 'running' &&
            (Number(a.effect?.getComputedTiming().endTime) || 0) > 100,
        )
        .map((a) => {
          const target = a.effect instanceof KeyframeEffect ? a.effect.target : null
          return `${a.animationName} on ${target ? `${target.tagName.toLowerCase()}.${[...target.classList].join('.')}` : '?'}`
        }),
    )
  } finally {
    await page.emulateMedia({ reducedMotion: reduced ? 'reduce' : 'no-preference' })
  }
}

async function structureProblems(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const shown = (el: Element) => (el as HTMLElement).checkVisibility?.() ?? true
    const problems: string[] = []
    const h1s = [...document.querySelectorAll('h1')].filter(shown)
    if (h1s.length !== 1) {
      problems.push(`${h1s.length} h1 elements: ${h1s.map((h) => JSON.stringify(h.textContent?.trim())).join(', ')}`)
    }
    const mains = document.querySelectorAll('main, [role="main"]').length
    if (mains !== 1) problems.push(`${mains} main landmarks`)
    for (const control of document.querySelectorAll('[aria-invalid="true"]')) {
      if (!shown(control)) continue
      const name = control.getAttribute('aria-label') ?? control.getAttribute('name') ?? control.id
      const ids = control.getAttribute('aria-describedby')?.split(/\s+/).filter(Boolean) ?? []
      const text = ids
        .map((id) => document.getElementById(id)?.textContent?.trim() ?? '')
        .join(' ')
        .trim()
      if (text === '') problems.push(`invalid control "${name}" has no message tied by aria-describedby`)
    }
    return problems
  })
}

/** WCAG 1.4.10: content reflows at 320 CSS px, and at 640 (200% zoom of 1280), without scrolling sideways. */
async function reflowProblems(page: Page): Promise<string[]> {
  const original = page.viewportSize()
  if (!original) return []
  const problems: string[] = []
  try {
    for (const width of [320, 640]) {
      await page.setViewportSize({ width, height: original.height })
      await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))))
      const overflow = await page.evaluate(() => {
        const root = document.documentElement
        if (root.scrollWidth <= root.clientWidth) return []
        const limit = root.clientWidth
        // The outermost elements that reach past the viewport's right edge, which is what scrolls.
        // Inside a box that scrolls or clips sideways, such as a strip of chips or a table's own scroller, is no overflow.
        const contained = (el: Element) => {
          for (let a = el.parentElement; a && a !== document.body; a = a.parentElement) {
            if (getComputedStyle(a).overflowX !== 'visible') return true
          }
          return false
        }
        const wide = [...document.body.querySelectorAll('*')].filter((el) => {
          if (el.getBoundingClientRect().right <= limit + 1 || contained(el)) return false
          const parent = el.parentElement
          return !parent || parent === document.body || parent.getBoundingClientRect().right <= limit + 1
        })
        const describe = (el: Element) =>
          `${el.tagName.toLowerCase()}${el.id ? `#${el.id}` : ''}${[...el.classList].map((c) => `.${c}`).join('')}`
        return [`scrolls to ${root.scrollWidth}px: ${wide.slice(0, 5).map(describe).join(', ')}`]
      })
      problems.push(...overflow.map((p) => `at ${width}px, ${p}`))
    }
  } finally {
    await page.setViewportSize(original)
  }
  return problems
}

/**
 * A keyboard-only walk (#144): Tab from the top of the page until focus comes round again, failing
 * on a control focus reaches without a visible focus indicator, and on a trap, which leaves out a
 * control it should have reached. An open modal dialog is walked alone, as it keeps focus inside.
 */
export async function expectKeyboardWalk(page: Page): Promise<void> {
  const where = page.url()
  // Tab goes on from where focus or the last click was, so the walk starts from a mark put first in
  // the page (or the open modal), taken out again once Tab has moved on from it.
  await page.evaluate(() => {
    window.getSelection()?.removeAllRanges()
    const mark = document.createElement('span')
    mark.tabIndex = -1
    mark.dataset.walkStart = ''
    const scope = document.querySelector('dialog:modal') ?? document.body
    scope.prepend(mark)
    mark.focus()
    mark.addEventListener('blur', () => mark.remove(), { once: true })
  })
  const seen: string[] = []
  const unringed: string[] = []
  for (let step = 0; step < 300; step++) {
    await page.keyboard.press('Tab')
    const focused = await page.evaluate(() => {
      const el = document.activeElement as HTMLElement | null
      if (!el || el === document.body) return null
      // Each element tags itself once, so the walk can tell when focus has come round.
      el.dataset.walk ??= String(document.querySelectorAll('[data-walk]').length)
      const outlined = (target: Element) => {
        const style = getComputedStyle(target)
        const transparent = style.outlineColor === 'transparent' || /rgba\(.*,\s*0\)$/.test(style.outlineColor)
        return style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) > 0 && !transparent
      }
      // A visually hidden radio or checkbox shows its ring on the label around it.
      const ringed = outlined(el) || [el.parentElement, el.parentElement?.parentElement].some((a) => a && outlined(a))
      const name = el.getAttribute('aria-label') ?? el.textContent?.trim().slice(0, 40) ?? ''
      return { key: el.dataset.walk, label: `${el.tagName.toLowerCase()} "${name}"`, ringed }
    })
    if (!focused) {
      if (seen.length > 0) break
      continue
    }
    // A date input's month, day and year are Tab stops within one control.
    if (focused.key === seen.at(-1)) continue
    if (seen.includes(focused.key)) break
    seen.push(focused.key)
    if (!focused.ringed) unringed.push(focused.label)
  }
  const missed = await page.evaluate(() => {
    // Only a modal dialog keeps focus in; Help, shown without one, leaves the page reachable.
    const scope = document.querySelector('dialog:modal, [aria-modal="true"]') ?? document
    const tabbable = [
      ...scope.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"]), summary',
      ),
    ].filter((el) => el.tabIndex >= 0 && el.checkVisibility() && !el.closest('[inert]'))
    // A radio group is one stop: only its checked radio, or its first, takes focus.
    const reachable = tabbable.filter((el) => {
      if (!(el instanceof HTMLInputElement) || el.type !== 'radio' || !el.name) return true
      const group = tabbable.filter(
        (other): other is HTMLInputElement =>
          other instanceof HTMLInputElement && other.type === 'radio' && other.name === el.name,
      )
      return el === (group.find((r) => r.checked) ?? group[0])
    })
    const missing = reachable
      .filter((el) => el.dataset.walk === undefined)
      .map(
        (el) => `${el.tagName.toLowerCase()} "${el.getAttribute('aria-label') ?? el.textContent?.trim().slice(0, 40)}"`,
      )
    for (const el of document.querySelectorAll<HTMLElement>('[data-walk]')) delete el.dataset.walk
    return missing
  })
  expect.soft(unringed, `controls without a visible focus indicator on ${where}`).toEqual([])
  expect.soft(missed, `controls Tab never reached on ${where}`).toEqual([])
}
