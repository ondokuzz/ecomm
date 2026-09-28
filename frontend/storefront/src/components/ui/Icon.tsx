import type { SVGProps } from 'react'

const paths = {
  cart: 'M3 4h2l2.2 10.2a2 2 0 0 0 2 1.6h7.6a2 2 0 0 0 2-1.5L20.5 8H6.2 M10 20.5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0z M18 20.5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0z',
  menu: 'M4 7h16 M4 12h16 M4 17h16',
  close: 'M6 6l12 12 M18 6L6 18',
  minus: 'M5 12h14',
  plus: 'M12 5v14 M5 12h14',
  alert: 'M12 8v5 M12 16.5v.01 M10.3 3.9L2.4 17.6A2 2 0 0 0 4.1 20.6h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z',
  check: 'M20 6L9 17l-5-5',
  arrowLeft: 'M19 12H5 M11 18l-6-6 6-6',
  logout: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3 M10 17l5-5-5-5 M15 12H4',
  bag: 'M6 7h12l1 13H5L6 7z M9 7a3 3 0 0 1 6 0',
} as const

export type IconName = keyof typeof paths

/** A stroke icon from the Storefront's small set; decorative unless given a `title`. */
export function Icon({
  name,
  size = 20,
  title,
  ...props
}: { name: IconName; size?: number; title?: string } & SVGProps<SVGSVGElement>) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true}
      {...props}
    >
      {title && <title>{title}</title>}
      <path d={paths[name]} />
    </svg>
  )
}
