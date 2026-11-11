/**
 * How the Storefront writes an instant, in the browser's locale and time zone, so every page says
 * a date the same way: an event with its time, a day alone, or a time of day alone. `locale` and
 * `timeZone` are for the tests.
 */
export function formatDateTime(at: string, locale?: string, timeZone?: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short', timeZone }).format(new Date(at))
}

/** A day, such as a deadline or when an Order was placed, in a list. */
export function formatDay(at: string, locale?: string, timeZone?: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone }).format(new Date(at))
}

/** A time later today, such as when a hold ends or Help can be asked again. */
export function formatClock(at: string | Date, locale?: string, timeZone?: string): string {
  return new Intl.DateTimeFormat(locale, { hour: 'numeric', minute: '2-digit', timeZone }).format(new Date(at))
}
