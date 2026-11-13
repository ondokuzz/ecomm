# Brand proposals (#146)

Three proposals for the brand that replaces the purple, each a logo and the Storefront's colour tokens in light and dark. They are shown on the running Storefront's home and Product pages and Keycloak's sign-in page, in a page published for the product owner to compare: https://claude.ai/artifact/Uh3EFmqzL2kPPPtwbYDAez. #147 builds the pick.

**The pick (2026-11-13, on #146):** Lagoon's colours, with Graphite & Citrus's mark in Lagoon's colours: [`pick.svg`](pick.svg). It is the rounded square with the "e" and the corner tab, in Lagoon's teal gradient with a coral tab (`#ff7a52`, the coral of Lagoon's `--gradient-sun`). It reads on both Lagoon backgrounds, so it needs no dark variant.

Each table lists every colour token the proposal sets in `frontend/storefront/src/index.css`. The tokens it doesn't list (fonts, sizes, spacing, radii) are unchanged. Every text colour on the backgrounds it is used on meets WCAG AA (4.5:1), and `--color-primary`, the focus ring, meets 3:1 on each background, in both modes. The marks are 64 × 64 SVGs beside this file.

The header's wordmark shows today in small uppercase letters, because `.brand-name` is also the product cards' manufacturer label (`index.css`). The screenshots set it as the wordmark it was meant to be; #147 renames one of the two. Below 24 rem the same shared class hides the product cards' label along with the wordmark.

The tokens `--shadow-primary` and `--shadow-primary-hover` follow `--color-primary`, so no table lists them. "As today" means the value `index.css` held before #147.

## A. Lagoon

Deep teal with a coral accent. Calm and trustworthy, the colour of clear water; nothing else in the store is teal, so the brand never reads as a status.

Mark: [`lagoon.svg`](lagoon.svg). Contrast: 78 of 78 pairs pass.

| Token | Light | Dark |
|---|---|---|
| `--color-bg` | `#f4faf9` | `#061716` |
| `--color-surface` | `#ffffff` | `#0d2423` |
| `--color-surface-2` | `#e4f2f0` | `#14322f` |
| `--color-fg` | `#0b2523` | `#ecfaf8` |
| `--color-muted` | `#4a5f5c` | `#a1c1bc` |
| `--color-line` | `#d3e5e2` | `#1e403d` |
| `--color-line-strong` | `#a7c9c4` | `#2f5a56` |
| `--color-primary` | `#0b7a75` | `#4fd1c5` |
| `--color-primary-hover` | `#08625e` | `#76ddd3` |
| `--color-primary-soft` | `#d6f1ed` | `#123f3b` |
| `--color-on-primary` | `#ffffff` | `#03201e` |
| `--color-primary-text` | `#08706b` | `#6fdace` |
| `--color-accent` | `#c8401f` | `#ff8a65` |
| `--color-accent-soft` | `#ffe6dd` | `#4a1e12` |
| `--color-accent-text` | `#a5341a` | `#ffa486` |
| `--color-on-accent` | `#ffffff` | `#2a0a00` |
| `--color-success` | `#127a3e` | `#5ee39a` |
| `--color-success-soft` | `#dcf7e6` | `#123a26` |
| `--color-danger` | `#b4231b` | `#ff8a80` |
| `--color-danger-soft` | `#fde4e2` | `#4a1714` |
| `--color-warning` | `#8a5300` | `#ffcb66` |
| `--color-warning-soft` | `#fff0cc` | `#3d2c08` |
| `--color-info` | `#0b63a8` | `#7cc4ff` |
| `--color-info-soft` | `#dcefff` | `#0f2d4a` |
| `--color-sun` | `#ffc233` | `#ffcc4d` |
| `--color-on-sun` | `#2b1a00` | `#2b1a00` |
| `--gradient-brand` | `linear-gradient(120deg, #08605c 0%, #0b7a75 55%, #0f5f7a 100%)` | `linear-gradient(120deg, #08605c 0%, #0b7a75 55%, #0f5f7a 100%)` |
| `--color-on-gradient` | `#ffffff` | `#ffffff` |
| `--color-scrim` | `#0b252366` | `#000000a6` |
| `--gradient-sun` | `linear-gradient(120deg, #ffc233 0%, #ff7a52 100%)` | as today |
| `--gradient-glow` | `radial-gradient(60rem 30rem at 0% 0%, #0b7a751f, transparent 60%), radial-gradient(50rem 30rem at 100% 0%, #ff7a5217, transparent 60%)` | `radial-gradient(60rem 30rem at 0% 0%, #4fd1c533, transparent 60%), radial-gradient(50rem 30rem at 100% 0%, #ff8a6526, transparent 60%)` |
| `--shadow-sm` | `0 1px 2px #0b252314, 0 1px 1px #0b25230a` | as today |
| `--shadow-md` | `0 6px 16px -4px #0b252324, 0 2px 4px #0b25230f` | as today |
| `--shadow-lg` | `0 18px 40px -12px #0b3d3a40, 0 4px 10px #0b252314` | as today |

## B. Cobalt

Confident cobalt blue with an amber accent. Familiar, the blue of banks and payment pages, so trust comes first; amber makes offers and Stock stand out.

Mark: [`cobalt.svg`](cobalt.svg). Contrast: 78 of 78 pairs pass.

| Token | Light | Dark |
|---|---|---|
| `--color-bg` | `#f5f7ff` | `#090e21` |
| `--color-surface` | `#ffffff` | `#111935` |
| `--color-surface-2` | `#e8edff` | `#1a2447` |
| `--color-fg` | `#0e1530` | `#eef2ff` |
| `--color-muted` | `#515a78` | `#a8b2d6` |
| `--color-line` | `#dbe2f6` | `#26315b` |
| `--color-line-strong` | `#b6c2ea` | `#39467e` |
| `--color-primary` | `#2443d0` | `#8ca2ff` |
| `--color-primary-hover` | `#1b34aa` | `#a8b9ff` |
| `--color-primary-soft` | `#e1e8ff` | `#1e2b62` |
| `--color-on-primary` | `#ffffff` | `#0a1236` |
| `--color-primary-text` | `#2140c6` | `#a3b4ff` |
| `--color-accent` | `#f5a524` | `#fbbf24` |
| `--color-accent-soft` | `#fff1d6` | `#3d2c08` |
| `--color-accent-text` | `#8f5200` | `#fcd34d` |
| `--color-on-accent` | `#2b1a00` | `#2b1a00` |
| `--color-success` | `#127a3e` | `#5ee39a` |
| `--color-success-soft` | `#dcf7e6` | `#123a26` |
| `--color-danger` | `#b4231b` | `#ff8a80` |
| `--color-danger-soft` | `#fde4e2` | `#4a1714` |
| `--color-warning` | `#8a5300` | `#ffcb66` |
| `--color-warning-soft` | `#fff0cc` | `#3d2c08` |
| `--color-info` | `#08707d` | `#5fd6e4` |
| `--color-info-soft` | `#d8f3f6` | `#0b3238` |
| `--color-sun` | `#ffc233` | `#ffcc4d` |
| `--color-on-sun` | `#2b1a00` | `#2b1a00` |
| `--gradient-brand` | `linear-gradient(120deg, #1b34aa 0%, #2443d0 55%, #1f5fb8 100%)` | `linear-gradient(120deg, #1b34aa 0%, #2443d0 55%, #1f5fb8 100%)` |
| `--color-on-gradient` | `#ffffff` | `#ffffff` |
| `--color-scrim` | `#0e153066` | `#000000a6` |
| `--gradient-sun` | `linear-gradient(120deg, #ffc233 0%, #f5a524 100%)` | as today |
| `--gradient-glow` | `radial-gradient(60rem 30rem at 0% 0%, #2443d01f, transparent 60%), radial-gradient(50rem 30rem at 100% 0%, #f5a5241c, transparent 60%)` | `radial-gradient(60rem 30rem at 0% 0%, #2443d040, transparent 60%), radial-gradient(50rem 30rem at 100% 0%, #fbbf2420, transparent 60%)` |
| `--shadow-sm` | `0 1px 2px #0e153014, 0 1px 1px #0e15300a` | as today |
| `--shadow-md` | `0 6px 16px -4px #0e153024, 0 2px 4px #0e15300f` | as today |
| `--shadow-lg` | `0 18px 40px -12px #1b2a7040, 0 4px 10px #0e153014` | as today |

## C. Graphite & Citrus

Near-black ink with a citrus-lime accent. Editorial and modern; the Product photos carry the colour, and lime marks only what to press or notice.

Mark: [`graphite.svg`](graphite.svg), and on dark backgrounds [`graphite-dark.svg`](graphite-dark.svg). Contrast: 78 of 78 pairs pass.

| Token | Light | Dark |
|---|---|---|
| `--color-bg` | `#f7f7f4` | `#0e0e10` |
| `--color-surface` | `#ffffff` | `#18181b` |
| `--color-surface-2` | `#eeeee9` | `#232327` |
| `--color-fg` | `#16171a` | `#f4f4f0` |
| `--color-muted` | `#575960` | `#b0b0a9` |
| `--color-line` | `#e2e2dc` | `#2e2e33` |
| `--color-line-strong` | `#c3c3bb` | `#46464d` |
| `--color-primary` | `#18181b` | `#c8f031` |
| `--color-primary-hover` | `#3a3a41` | `#daff5c` |
| `--color-primary-soft` | `#ebf7c4` | `#2a320f` |
| `--color-on-primary` | `#ffffff` | `#16170a` |
| `--color-primary-text` | `#4a6b00` | `#cff24f` |
| `--color-accent` | `#c8f031` | `#c8f031` |
| `--color-accent-soft` | `#f1fbd0` | `#2a320f` |
| `--color-accent-text` | `#4a6b00` | `#d6f566` |
| `--color-on-accent` | `#16170a` | `#16170a` |
| `--color-success` | `#127a3e` | `#5ee39a` |
| `--color-success-soft` | `#dcf7e6` | `#123a26` |
| `--color-danger` | `#b4231b` | `#ff8a80` |
| `--color-danger-soft` | `#fde4e2` | `#4a1714` |
| `--color-warning` | `#8a5300` | `#ffcb66` |
| `--color-warning-soft` | `#fff0cc` | `#3d2c08` |
| `--color-info` | `#0b63a8` | `#7cc4ff` |
| `--color-info-soft` | `#dcefff` | `#0f2d4a` |
| `--color-sun` | `#ffc233` | `#ffcc4d` |
| `--color-on-sun` | `#2b1a00` | `#2b1a00` |
| `--gradient-brand` | `linear-gradient(120deg, #18181b 0%, #26272c 55%, #3d5a00 100%)` | `linear-gradient(120deg, #18181b 0%, #26272c 55%, #3d5a00 100%)` |
| `--color-on-gradient` | `#ffffff` | `#ffffff` |
| `--color-scrim` | `#16171a66` | `#000000a6` |
| `--gradient-sun` | `linear-gradient(120deg, #c8f031 0%, #ffd23f 100%)` | as today |
| `--gradient-glow` | `radial-gradient(60rem 30rem at 0% 0%, #c8f03126, transparent 60%), radial-gradient(50rem 30rem at 100% 0%, #18181b0d, transparent 60%)` | `radial-gradient(60rem 30rem at 0% 0%, #c8f0311f, transparent 60%), radial-gradient(50rem 30rem at 100% 0%, #ffffff0a, transparent 60%)` |
| `--shadow-sm` | `0 1px 2px #16171a14, 0 1px 1px #16171a0a` | as today |
| `--shadow-md` | `0 6px 16px -4px #16171a24, 0 2px 4px #16171a0f` | as today |
| `--shadow-lg` | `0 18px 40px -12px #16171a40, 0 4px 10px #16171a14` | as today |

## The pairs checked

In each mode, each proposal's foreground on background, with the ratio WCAG AA asks of it. A gradient's text is checked on each of its stops. The check was a script run over the tables above; #147's test over the tokens takes these pairs.

| Foreground | Background | Needs |
|---|---|---|
| `--color-fg` | `--color-bg` | 4.5:1 |
| `--color-fg` | `--color-surface` | 4.5:1 |
| `--color-fg` | `--color-surface-2` | 4.5:1 |
| `--color-fg` | `--color-primary-soft` | 4.5:1 |
| `--color-fg` | `--color-accent-soft` | 4.5:1 |
| `--color-muted` | `--color-bg` | 4.5:1 |
| `--color-muted` | `--color-surface` | 4.5:1 |
| `--color-muted` | `--color-surface-2` | 4.5:1 |
| `--color-primary-text` | `--color-bg` | 4.5:1 |
| `--color-primary-text` | `--color-surface` | 4.5:1 |
| `--color-primary-text` | `--color-surface-2` | 4.5:1 |
| `--color-primary-text` | `--color-primary-soft` | 4.5:1 |
| `--color-on-primary` | `--color-primary` | 4.5:1 |
| `--color-on-primary` | `--color-primary-hover` | 4.5:1 |
| `--color-accent-text` | `--color-bg` | 4.5:1 |
| `--color-accent-text` | `--color-surface` | 4.5:1 |
| `--color-accent-text` | `--color-accent-soft` | 4.5:1 |
| `--color-on-accent` | `--color-accent` | 4.5:1 |
| `--color-on-sun` | `--color-sun` | 4.5:1 |
| `--color-success` | `--color-bg` | 4.5:1 |
| `--color-success` | `--color-surface` | 4.5:1 |
| `--color-success` | `--color-success-soft` | 4.5:1 |
| `--color-danger` | `--color-bg` | 4.5:1 |
| `--color-danger` | `--color-surface` | 4.5:1 |
| `--color-danger` | `--color-danger-soft` | 4.5:1 |
| `--color-warning` | `--color-bg` | 4.5:1 |
| `--color-warning` | `--color-surface` | 4.5:1 |
| `--color-warning` | `--color-warning-soft` | 4.5:1 |
| `--color-info` | `--color-bg` | 4.5:1 |
| `--color-info` | `--color-surface` | 4.5:1 |
| `--color-info` | `--color-info-soft` | 4.5:1 |
| `--color-surface` | `--color-danger` | 4.5:1 |
| `--color-surface` | `--color-warning` | 4.5:1 |
| `--color-on-gradient` | each `--gradient-brand` stop | 4.5:1 |
| `--color-primary` | `--color-bg` | 3:1 |
| `--color-primary` | `--color-surface` | 3:1 |
| `--color-primary` | `--color-surface-2` | 3:1 |
