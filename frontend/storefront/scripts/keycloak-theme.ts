/**
 * Copies what the Keycloak login theme shares with the Storefront into the theme's resources: the
 * design tokens from src/index.css, the two variable fonts with their @font-face rules, and the
 * favicon. The theme's own stylesheet (ecomm.css) is written by hand against these and is not
 * touched.
 *
 * Run it after changing the tokens, the fonts or the favicon: `npm run keycloak-theme`.
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const storefrontUrl = new URL('../', import.meta.url)
const resourcesUrl = new URL('../../../services/identity-access/themes/ecomm/login/resources/', import.meta.url)

/** Keycloak's login pages put this class on <html> when dark mode is on for the realm and the OS. */
const darkModeClass = 'pf-v5-theme-dark'

/** The Storefront's fonts, by fontsource package and the one Latin, upright file the theme needs. */
export const fonts = [
  { pkg: 'inter', face: 'inter-latin-wght-normal' },
  { pkg: 'bricolage-grotesque', face: 'bricolage-grotesque-latin-wght-normal' },
]

export interface ThemeFile {
  /** Path under the theme's resources/. */
  path: string
  /** Absolute path. */
  target: string
  content: Buffer
}

/**
 * The Storefront's design tokens, light and dark, as a stylesheet of their own. The light tokens
 * stay on `:root`; the dark ones move from the colour-scheme media query onto Keycloak's dark-mode
 * class, so they switch together with the PatternFly styles underneath.
 */
export function brandTokensCss(storefrontCss: string): string {
  const light = ruleBody(storefrontCss, storefrontCss.indexOf(':root'))
  const media = storefrontCss.indexOf('@media (prefers-color-scheme: dark)')
  if (media < 0) throw new Error('No dark-mode tokens found in the Storefront stylesheet')
  const dark = ruleBody(storefrontCss, storefrontCss.indexOf(':root', media))
  return (
    '/* Generated from the Storefront\'s src/index.css by scripts/keycloak-theme.ts; do not edit. */\n' +
    `:root ${dedent(light)}\n\n` +
    `:root.${darkModeClass} ${dedent(dark)}\n`
  )
}

/**
 * The @font-face rule fontsource writes for one font file, pointing at the theme's fonts/ folder
 * instead of fontsource's files/.
 */
export function fontFaceCss(fontsourceCss: string, face: string): string {
  const comment = fontsourceCss.indexOf(`/* ${face} */`)
  if (comment < 0) throw new Error(`No @font-face rule for ${face}`)
  const start = fontsourceCss.indexOf('@font-face', comment)
  return `@font-face ${ruleBody(fontsourceCss, start)}`.replaceAll('url(./files/', 'url(../fonts/')
}

/** Every file the script writes, with what it should hold. */
export function themeFiles(): ThemeFile[] {
  const read = (path: string) => readFileSync(fileURLToPath(new URL(path, storefrontUrl)))
  const fontsource = (pkg: string, path: string) => read(`node_modules/@fontsource-variable/${pkg}/${path}`)
  const inTheme = (path: string, content: Buffer) => ({
    path,
    target: fileURLToPath(new URL(path, resourcesUrl)),
    content,
  })
  return [
    inTheme('css/tokens.css', Buffer.from(brandTokensCss(read('src/index.css').toString()))),
    inTheme(
      'css/fonts.css',
      Buffer.from(
        '/* Generated from the Storefront\'s fontsource packages by scripts/keycloak-theme.ts; do not edit. */\n' +
          fonts.map(({ pkg, face }) => fontFaceCss(fontsource(pkg, 'wght.css').toString(), face)).join('\n\n') +
          '\n',
      ),
    ),
    ...fonts.flatMap(({ pkg, face }) => [
      inTheme(`fonts/${face}.woff2`, fontsource(pkg, `files/${face}.woff2`)),
      inTheme(`fonts/${pkg}-LICENSE.txt`, fontsource(pkg, 'LICENSE')),
    ]),
    inTheme('img/favicon.svg', read('public/favicon.svg')),
  ]
}

/** The `{ ... }` body of the rule that starts at `from`. */
export function ruleBody(css: string, from: number): string {
  if (from < 0) throw new Error('Rule not found')
  const open = css.indexOf('{', from)
  let depth = 0
  for (let i = open; i < css.length; i++) {
    if (css[i] === '{') depth++
    if (css[i] === '}' && --depth === 0) return css.slice(open, i + 1)
  }
  throw new Error('Unbalanced braces')
}

/** A nested block's lines, shifted back to the top level's two-space indent. */
function dedent(css: string): string {
  const lines = css.split('\n')
  const closing = lines.at(-1)!
  const extra = new RegExp(`^ {0,${closing.length - 1}}`)
  return lines.map((line, i) => (i === 0 ? line : line.replace(extra, ''))).join('\n')
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  for (const { target, content } of themeFiles()) {
    mkdirSync(dirname(target), { recursive: true })
    writeFileSync(target, content)
  }
  console.log(`Wrote the tokens, fonts and favicon to ${fileURLToPath(resourcesUrl)}`)
}
