import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { brandTokensCss, fontFaceCss, themeFiles } from './keycloak-theme.ts'

const storefrontCss = `
/* Design tokens. */
:root {
  color-scheme: light;
  --color-bg: #faf7ff;
  --gradient-brand: linear-gradient(120deg, #6a2cf5 0%, #d9381e 100%);
}

@media (prefers-color-scheme: dark) {
  :root {
    color-scheme: dark;
    --color-bg: #0e0a1c;
  }
}

body {
  margin: 0;
}
`

describe('brandTokensCss', () => {
  const css = brandTokensCss(storefrontCss)

  it('keeps the light tokens on :root', () => {
    expect(css).toContain(':root {\n  color-scheme: light;\n  --color-bg: #faf7ff;')
    expect(css).toContain('--gradient-brand: linear-gradient(120deg, #6a2cf5 0%, #d9381e 100%);')
  })

  it("puts the dark tokens on Keycloak's dark-mode class, so they follow the realm's dark mode", () => {
    expect(css).toContain(':root.pf-v5-theme-dark {\n  color-scheme: dark;\n  --color-bg: #0e0a1c;\n}')
    expect(css).not.toContain('@media')
  })

  it('leaves out everything but the tokens', () => {
    expect(css).not.toContain('body')
  })
})

describe('fontFaceCss', () => {
  const fontsourceCss = `
/* inter-cyrillic-wght-normal */
@font-face {
  font-family: 'Inter Variable';
  src: url(./files/inter-cyrillic-wght-normal.woff2) format('woff2-variations');
}

/* inter-latin-wght-normal */
@font-face {
  font-family: 'Inter Variable';
  src: url(./files/inter-latin-wght-normal.woff2) format('woff2-variations');
}
`

  it("takes the font's own rule and points it at the theme's fonts folder", () => {
    expect(fontFaceCss(fontsourceCss, 'inter-latin-wght-normal')).toBe(
      "@font-face {\n  font-family: 'Inter Variable';\n" +
        "  src: url(../fonts/inter-latin-wght-normal.woff2) format('woff2-variations');\n}",
    )
  })
})

describe('the Keycloak login theme', () => {
  it.each(themeFiles())('has an up-to-date $path (run `npm run keycloak-theme`)', ({ target, content }) => {
    expect(readFileSync(target)).toEqual(content)
  })
})
