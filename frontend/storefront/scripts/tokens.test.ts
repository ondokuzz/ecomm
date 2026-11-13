import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { contrastRatio, designTokens, gradientStops } from './tokens.ts'

describe('contrastRatio', () => {
  it("gives WCAG's ratios: 21:1 for black on white, 1:1 for a colour on itself", () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 5)
    expect(contrastRatio('#ffffff', '#000000')).toBeCloseTo(21, 5)
    expect(contrastRatio('#0b7a75', '#0b7a75')).toBe(1)
  })

  it('puts #767676 on white just above 4.5:1 and #777777 just below', () => {
    expect(contrastRatio('#767676', '#ffffff')).toBeCloseTo(4.54, 2)
    expect(contrastRatio('#777777', '#ffffff')).toBeLessThan(4.5)
  })
})

describe('designTokens', () => {
  const css = `
:root {
  --color-bg: #faf7ff;
  --gradient-brand: linear-gradient(120deg, #6a2cf5 0%, #d9381e 100%);
  --space-1: 0.25rem;
}
@media (prefers-color-scheme: dark) {
  :root {
    --color-bg: #0e0a1c;
  }
}
.card { --color-bg: #123456; }
`

  it('reads the light tokens, and the dark ones as the light ones with dark mode’s overrides', () => {
    const { light, dark } = designTokens(css)
    expect(light['color-bg']).toBe('#faf7ff')
    expect(dark['color-bg']).toBe('#0e0a1c')
    expect(dark['gradient-brand']).toBe(light['gradient-brand'])
    expect(light['space-1']).toBe('0.25rem')
  })

  it("finds a gradient's colour stops", () => {
    expect(gradientStops(designTokens(css).light['gradient-brand'])).toEqual(['#6a2cf5', '#d9381e'])
  })
})

/**
 * Each foreground on each background it is used on, and the ratio WCAG AA asks of it: 4.5:1 for
 * text, 3:1 for the focus ring (`--color-primary`). The list is docs/brand/proposals.md's, "The
 * pairs checked".
 */
const text = (fg: string, bgs: string[]) => bgs.map((bg) => [fg, bg, 4.5] as const)
const pairs = [
  ...text('color-fg', ['color-bg', 'color-surface', 'color-surface-2', 'color-primary-soft', 'color-accent-soft']),
  ...text('color-muted', ['color-bg', 'color-surface', 'color-surface-2']),
  ...text('color-primary-text', ['color-bg', 'color-surface', 'color-surface-2', 'color-primary-soft']),
  ...text('color-on-primary', ['color-primary', 'color-primary-hover']),
  ...text('color-accent-text', ['color-bg', 'color-surface', 'color-accent-soft']),
  ...text('color-on-accent', ['color-accent']),
  ...text('color-on-sun', ['color-sun']),
  ...['success', 'danger', 'warning', 'info'].flatMap((s) =>
    text(`color-${s}`, ['color-bg', 'color-surface', `color-${s}-soft`]),
  ),
  ...text('color-surface', ['color-danger', 'color-warning']),
  ['color-primary', 'color-bg', 3],
  ['color-primary', 'color-surface', 3],
  ['color-primary', 'color-surface-2', 3],
] as const

describe("the Storefront's design tokens", () => {
  const tokens = designTokens(readFileSync(fileURLToPath(new URL('../src/index.css', import.meta.url)), 'utf8'))

  describe.each(['light', 'dark'] as const)('in %s mode', (mode) => {
    const t = tokens[mode]

    it.each(pairs)('--%s on --%s meets %s:1', (fg, bg, min) => {
      expect(contrastRatio(t[fg], t[bg])).toBeGreaterThanOrEqual(min)
    })

    it.each(gradientStops(t['gradient-brand']))(
      '--color-on-gradient on the brand gradient’s %s meets 4.5:1',
      (stop) => {
        expect(contrastRatio(t['color-on-gradient'], stop)).toBeGreaterThanOrEqual(4.5)
      },
    )
  })
})
