/**
 * Draws an SVG illustration for each of Catalog's seed Products and writes it to public/ at the
 * image path the seed gives it, so Vite (dev) and nginx (compose) serve it on that path. The art is
 * generated, not photographed: a device shape per kind of Product, in the Product's colour, on a
 * backdrop in its brand's colour from the Storefront's palette.
 *
 * Run it after changing the seed: `npm run images`.
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { type SeedProduct, publicFile, seedProducts } from './seed.ts'

/** Bold colours from the design tokens in src/index.css, one per brand. */
const brandColors: Record<string, string> = {
  Apple: '#db1664',
  Google: '#6a2cf5',
  Samsung: '#0b63a8',
  Nothing: '#ff6b3d',
  Dell: '#c026d3',
  Lenovo: '#d9381e',
  ASUS: '#6a2cf5',
  Framework: '#127a3e',
  Microsoft: '#0b63a8',
  Sony: '#c026d3',
  Bose: '#ff6b3d',
  Sennheiser: '#6a2cf5',
  JBL: '#d9381e',
  Sonos: '#127a3e',
}

/** The device's own colour, by its `color` attribute. */
const finishColors: Record<string, string> = {
  Obsidian: '#2a2a33',
  Porcelain: '#efe9df',
  Black: '#1f1f24',
  'Desert Titanium': '#c9a88a',
  'Onyx Black': '#202028',
  'Awesome Navy': '#26345c',
  White: '#f4f4f6',
}

/** The device's colour, by SKU, for Products without a `color` attribute. */
const finishBySku: Record<string, string> = {
  'LPT-MBA-13-M3': '#d7d9df',
  'LPT-MBP-14-M4': '#3a3a42',
  'LPT-XPS-13': '#c0c3cc',
  'LPT-THINKPAD-X1': '#26262b',
  'LPT-ZENBOOK-14': '#3a4a78',
  'LPT-FRAMEWORK-13': '#b8bbc4',
  'LPT-SURFACE-7': '#cfd3dc',
  'AUD-AIRPODS-PRO-2': '#f7f7f9',
  'AUD-SONY-WH1000XM5': '#2b2b30',
  'AUD-BOSE-QC-ULTRA': '#e8e2d6',
  'AUD-SENNHEISER-MTW4': '#2b2b30',
  'AUD-JBL-FLIP-6': '#1e3a6e',
  'AUD-SONOS-ERA-100': '#f2f2f2',
}

const sun = '#ffc233'

interface Colors {
  brand: string
  finish: string
  edge: string
}

type Draw = (c: Colors) => string

const phone: Draw = ({ finish, edge }) =>
  `<g transform="rotate(-8 215 150)">` +
  `<rect x="160" y="40" width="110" height="220" rx="22" fill="${finish}" stroke="${edge}" stroke-width="3"/>` +
  `<rect x="168" y="48" width="94" height="204" rx="16" fill="url(#screen)"/>` +
  `<circle cx="215" cy="62" r="4" fill="#111"/>` +
  `</g>`

const laptop: Draw = ({ finish, edge }) =>
  `<rect x="90" y="55" width="220" height="148" rx="12" fill="${finish}" stroke="${edge}" stroke-width="3"/>` +
  `<rect x="101" y="66" width="198" height="126" rx="5" fill="url(#screen)"/>` +
  `<path d="M70 205h260l18 20a6 6 0 0 1-5 9H57a6 6 0 0 1-5-9z" fill="${finish}" stroke="${edge}" stroke-width="3"/>` +
  `<rect x="175" y="205" width="50" height="7" rx="3" fill="${edge}"/>`

const headphones: Draw = ({ brand, finish, edge }) =>
  `<path d="M128 175C128 60 272 60 272 175" fill="none" stroke="${edge}" stroke-width="20" stroke-linecap="round"/>` +
  `<path d="M128 175C128 60 272 60 272 175" fill="none" stroke="${finish}" stroke-width="14" stroke-linecap="round"/>` +
  `<rect x="100" y="148" width="56" height="92" rx="26" fill="${finish}" stroke="${edge}" stroke-width="3"/>` +
  `<rect x="244" y="148" width="56" height="92" rx="26" fill="${finish}" stroke="${edge}" stroke-width="3"/>` +
  `<rect x="146" y="160" width="14" height="68" rx="7" fill="${brand}"/>` +
  `<rect x="240" y="160" width="14" height="68" rx="7" fill="${brand}"/>`

const earbud = (x: number, flip: boolean, { finish, edge }: Colors) =>
  `<g transform="translate(${x} 58)${flip ? ' scale(-1 1)' : ''}">` +
  `<rect x="-6" y="18" width="14" height="52" rx="7" fill="${finish}" stroke="${edge}" stroke-width="3"/>` +
  `<ellipse cx="0" cy="18" rx="24" ry="20" fill="${finish}" stroke="${edge}" stroke-width="3"/>` +
  `<circle cx="-10" cy="16" r="7" fill="${edge}"/>` +
  `</g>`

const earbuds: Draw = (c) =>
  `<rect x="140" y="135" width="120" height="105" rx="38" fill="${c.finish}" stroke="${c.edge}" stroke-width="3"/>` +
  `<path d="M142 168h116" stroke="${c.edge}" stroke-width="3"/>` +
  `<circle cx="200" cy="205" r="5" fill="${c.brand}"/>` +
  earbud(172, false, c) +
  earbud(228, true, c)

const portableSpeaker: Draw = ({ brand, finish, edge }) =>
  `<g transform="rotate(-6 200 155)">` +
  `<rect x="100" y="108" width="200" height="94" rx="47" fill="${finish}" stroke="${edge}" stroke-width="3"/>` +
  `<path d="M130 130h140M122 145h156M122 160h156M130 175h140" stroke="${edge}" stroke-width="3" stroke-linecap="round" opacity=".6"/>` +
  `<ellipse cx="300" cy="155" rx="16" ry="44" fill="${brand}" stroke="${edge}" stroke-width="3"/>` +
  `<rect x="180" y="186" width="40" height="10" rx="5" fill="${sun}"/>` +
  `</g>`

const smartSpeaker: Draw = ({ brand, finish, edge }) => {
  const grille = [0, 1, 2, 3, 4, 5, 6]
    .flatMap((row) => [0, 1, 2, 3, 4].map((col) => `<circle cx="${168 + col * 16}" cy="${130 + row * 14}" r="3"/>`))
    .join('')
  return (
    `<rect x="145" y="62" width="110" height="182" rx="40" fill="${finish}" stroke="${edge}" stroke-width="3"/>` +
    `<ellipse cx="200" cy="88" rx="30" ry="9" fill="${brand}"/>` +
    `<g fill="${edge}" opacity=".5">${grille}</g>`
  )
}

function drawFor(product: SeedProduct): Draw {
  if (product.category === 'phones') return phone
  if (product.category === 'laptops') return laptop
  const type = product.attributes.type
  if (type === 'Over-ear') return headphones
  if (type === 'In-ear') return earbuds
  if (type === 'Portable speaker') return portableSpeaker
  if (type === 'Smart speaker') return smartSpeaker
  throw new Error(`No drawing for ${product.sku} (${product.category}, ${type})`)
}

/** Mixes two #rrggbb colours, `amount` of the way from `a` to `b`. */
function mix(a: string, b: string, amount: number): string {
  const channel = (hex: string, i: number) => parseInt(hex.slice(1 + i * 2, 3 + i * 2), 16)
  return (
    '#' +
    [0, 1, 2]
      .map((i) => Math.round(channel(a, i) + (channel(b, i) - channel(a, i)) * amount))
      .map((v) => v.toString(16).padStart(2, '0'))
      .join('')
  )
}

function productSvg(product: SeedProduct): string {
  const brand = brandColors[product.attributes.brand]
  const finish = finishColors[product.attributes.color] ?? finishBySku[product.sku]
  if (!brand || !finish) throw new Error(`No colours for ${product.sku}`)
  const colors: Colors = { brand, finish, edge: mix(finish, '#000000', 0.3) }
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 300" width="400" height="300">` +
    `<!-- Generated by scripts/generate-product-images.ts; do not edit. -->` +
    `<defs><linearGradient id="screen" x1="0" y1="0" x2="1" y2="1">` +
    `<stop offset="0" stop-color="${brand}"/><stop offset="1" stop-color="${mix(brand, '#ffffff', 0.45)}"/>` +
    `</linearGradient></defs>` +
    `<rect width="400" height="300" fill="${mix(brand, '#ffffff', 0.86)}"/>` +
    `<circle cx="200" cy="150" r="118" fill="${mix(brand, '#ffffff', 0.55)}"/>` +
    `<circle cx="326" cy="58" r="20" fill="${sun}"/>` +
    `<circle cx="70" cy="244" r="10" fill="${brand}"/>` +
    `<ellipse cx="200" cy="262" rx="120" ry="10" fill="${mix(brand, '#000000', 0.5)}" opacity=".18"/>` +
    drawFor(product)(colors) +
    `</svg>\n`
  )
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const seed = seedProducts()
  for (const product of seed) {
    for (const image of product.images) {
      const file = publicFile(image)
      mkdirSync(dirname(file), { recursive: true })
      writeFileSync(file, productSvg(product))
    }
  }
  console.log(`Wrote images for ${seed.length} Products to public/images/products/`)
}
