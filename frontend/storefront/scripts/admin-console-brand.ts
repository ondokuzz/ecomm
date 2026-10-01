/**
 * Copies the Storefront's look into the Admin Console, so both wear one brand with one source of
 * truth: the design tokens from src/index.css, the two variable fonts with their @font-face rules,
 * and the favicon. The Admin Console's own stylesheet is written by hand against these and is not
 * touched.
 *
 * Run it after changing the tokens, the fonts or the favicon: `npm run admin-console-brand`.
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { type ThemeFile, fontFaceCss, fonts, ruleBody } from './keycloak-theme.ts'

const storefrontUrl = new URL('../', import.meta.url)
const adminConsoleUrl = new URL('../../admin-console/', import.meta.url)

const darkModeQuery = '@media (prefers-color-scheme: dark)'

/**
 * The Storefront's design tokens, light and dark, as a stylesheet of their own. Dark mode stays on
 * the colour-scheme media query, as in the Storefront.
 */
export function adminTokensCss(storefrontCss: string): string {
  const light = ruleBody(storefrontCss, storefrontCss.indexOf(':root'))
  const media = storefrontCss.indexOf(darkModeQuery)
  if (media < 0) throw new Error('No dark-mode tokens found in the Storefront stylesheet')
  return (
    "/* Generated from the Storefront's src/index.css by its scripts/admin-console-brand.ts; do not edit. */\n" +
    `:root ${light}\n\n` +
    `${darkModeQuery} ${ruleBody(storefrontCss, media)}\n`
  )
}

/** Every file the script writes, with what it should hold. */
export function adminConsoleFiles(): ThemeFile[] {
  const read = (path: string) => readFileSync(fileURLToPath(new URL(path, storefrontUrl)))
  const fontsource = (pkg: string, path: string) => read(`node_modules/@fontsource-variable/${pkg}/${path}`)
  const inAdminConsole = (path: string, content: Buffer) => ({
    path,
    target: fileURLToPath(new URL(path, adminConsoleUrl)),
    content,
  })
  return [
    inAdminConsole('src/brand/tokens.css', Buffer.from(adminTokensCss(read('src/index.css').toString()))),
    inAdminConsole(
      'src/brand/fonts.css',
      Buffer.from(
        "/* Generated from the Storefront's fontsource packages by its scripts/admin-console-brand.ts; do not edit. */\n" +
          fonts
            .map(({ pkg, face }) =>
              fontFaceCss(fontsource(pkg, 'wght.css').toString(), face).replaceAll('url(../fonts/', 'url(./fonts/'),
            )
            .join('\n\n') +
          '\n',
      ),
    ),
    ...fonts.flatMap(({ pkg, face }) => [
      inAdminConsole(`src/brand/fonts/${face}.woff2`, fontsource(pkg, `files/${face}.woff2`)),
      inAdminConsole(`src/brand/fonts/${pkg}-LICENSE.txt`, fontsource(pkg, 'LICENSE')),
    ]),
    inAdminConsole('public/favicon.svg', read('public/favicon.svg')),
  ]
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  for (const { target, content } of adminConsoleFiles()) {
    mkdirSync(dirname(target), { recursive: true })
    writeFileSync(target, content)
  }
  console.log(`Wrote the tokens, fonts and favicon to ${fileURLToPath(adminConsoleUrl)}`)
}
