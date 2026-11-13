/**
 * Reads the Storefront's design tokens out of src/index.css, and measures colours against each
 * other as WCAG 2.2 does, so a test can hold every text shade to AA contrast.
 */
import { ruleBody } from './keycloak-theme.ts'

export type Tokens = Record<string, string>

/** The tokens on `:root`, light, and dark: the light ones with the dark-mode query's overrides. */
export function designTokens(css: string): { light: Tokens; dark: Tokens } {
  const light = declarations(ruleBody(css, css.indexOf(':root')))
  const media = css.indexOf('@media (prefers-color-scheme: dark)')
  if (media < 0) throw new Error('No dark-mode tokens found')
  return { light, dark: { ...light, ...declarations(ruleBody(css, media)) } }
}

/** A gradient's colour stops, as #rrggbb. */
export function gradientStops(gradient: string): string[] {
  return gradient.match(/#[0-9a-f]{6}\b/gi) ?? []
}

/** WCAG's contrast ratio of two #rrggbb colours, from 1:1 to 21:1. */
export function contrastRatio(a: string, b: string): number {
  const [lighter, darker] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (lighter + 0.05) / (darker + 0.05)
}

function luminance(hex: string): number {
  if (!/^#[0-9a-f]{6}$/i.test(hex)) throw new Error(`Not a #rrggbb colour: ${hex}`)
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function declarations(body: string): Tokens {
  const tokens: Tokens = {}
  for (const [, name, value] of body.matchAll(/--([\w-]+):\s*([^;]+);/g)) tokens[name] = value.trim()
  return tokens
}
