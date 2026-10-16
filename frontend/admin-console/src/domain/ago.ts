const minute = 60_000
const hour = 60 * minute
const day = 24 * hour

/**
 * How long before `now` an instant was, as a queue shows it: "just now" under a minute, then whole
 * minutes, hours or days, rounded down. An instant a little ahead of the browser's clock is just now.
 */
export function formatAgo(at: string, now: Date = new Date(), locale?: string): string {
  const elapsed = now.getTime() - new Date(at).getTime()
  if (elapsed < minute) return 'just now'
  const relative = new Intl.RelativeTimeFormat(locale, { numeric: 'always' })
  if (elapsed < hour) return relative.format(-Math.floor(elapsed / minute), 'minute')
  if (elapsed < day) return relative.format(-Math.floor(elapsed / hour), 'hour')
  return relative.format(-Math.floor(elapsed / day), 'day')
}
