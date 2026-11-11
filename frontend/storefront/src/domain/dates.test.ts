import { describe, expect, it } from 'vitest'
import { formatClock, formatDateTime, formatDay } from './dates'

// Noon UTC, so the day is the same in every time zone the tests might run in.
const at = '2026-03-07T12:05:00Z'
// Intl puts a narrow no-break space before AM and PM; compare with plain ones.
const plain = (text: string) => text.replace(/\s/g, ' ')

describe('dates', () => {
  it('writes an instant as a medium date and a short time', () => {
    expect(plain(formatDateTime(at, 'en-US', 'UTC'))).toBe('Mar 7, 2026, 12:05 PM')
  })

  it('writes the day alone', () => {
    expect(formatDay(at, 'en-US', 'UTC')).toBe('Mar 7, 2026')
  })

  it('writes the time of day alone', () => {
    expect(plain(formatClock(at, 'en-US', 'UTC'))).toBe('12:05 PM')
  })

  it("follows the browser's locale", () => {
    expect(formatDay(at, 'de-DE', 'UTC')).toBe('07.03.2026')
  })
})
