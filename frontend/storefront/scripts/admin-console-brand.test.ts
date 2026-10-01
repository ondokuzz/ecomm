import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { adminConsoleFiles, adminTokensCss } from './admin-console-brand.ts'

const storefrontCss = `
/* Design tokens. */
:root {
  color-scheme: light;
  --color-bg: #faf7ff;
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

describe('adminTokensCss', () => {
  const css = adminTokensCss(storefrontCss)

  it('keeps the light tokens on :root', () => {
    expect(css).toContain(':root {\n  color-scheme: light;\n  --color-bg: #faf7ff;\n}')
  })

  it('keeps the dark tokens under the colour-scheme query, as the Storefront has them', () => {
    expect(css).toContain(
      '@media (prefers-color-scheme: dark) {\n  :root {\n    color-scheme: dark;\n    --color-bg: #0e0a1c;\n  }\n}',
    )
  })

  it('leaves out everything but the tokens', () => {
    expect(css).not.toContain('body')
  })
})

describe('the Admin Console', () => {
  it.each(adminConsoleFiles())('has an up-to-date $path (run `npm run admin-console-brand`)', ({ target, content }) => {
    expect(readFileSync(target)).toEqual(content)
  })
})
