import type { SVGProps } from 'react'

const paths = {
  cart: 'M3 4h2l2.2 10.2a2 2 0 0 0 2 1.6h7.6a2 2 0 0 0 2-1.5L20.5 8H6.2 M10 20.5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0z M18 20.5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0z',
  menu: 'M4 7h16 M4 12h16 M4 17h16',
  minus: 'M5 12h14',
  plus: 'M12 5v14 M5 12h14',
  alert: 'M12 8v5 M12 16.5v.01 M10.3 3.9L2.4 17.6A2 2 0 0 0 4.1 20.6h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z',
  check: 'M20 6L9 17l-5-5',
  close: 'M18 6L6 18 M6 6l12 12',
  search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z M20 20l-4.3-4.3',
  arrowLeft: 'M19 12H5 M11 18l-6-6 6-6',
  arrowRight: 'M5 12h14 M13 6l6 6-6 6',
  trash: 'M4 7h16 M10 11v6 M14 11v6 M6 7l1 13h10l1-13 M9 7V4h6v3',
  lock: 'M6 11h12v10H6z M8 11V7a4 4 0 0 1 8 0v4',
  logout: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3 M10 17l5-5-5-5 M15 12H4',
  orders: 'M6 7h12l1 13H5L6 7z M9 7a3 3 0 0 1 6 0',
  image: 'M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z M4 16l5-5 4 4 2-2 5 5 M15 9v.01',
} as const

export type IconName = keyof typeof paths

/** A decorative stroke icon from the Storefront's small set; the control around it carries the name. */
export function Icon({ name, size = 20, ...props }: { name: IconName; size?: number } & SVGProps<SVGSVGElement>) {
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
      aria-hidden="true"
      {...props}
    >
      <path d={paths[name]} />
    </svg>
  )
}
