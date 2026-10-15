import { describe, expect, it } from 'vitest'
import { formatAgo } from './ago'

const now = new Date('2026-10-15T12:00:00Z')

describe('formatAgo', () => {
  it('says under a minute as just now', () => {
    expect(formatAgo('2026-10-15T11:59:31Z', now, 'en')).toBe('just now')
  })

  it('counts minutes, then hours, then days, rounding down', () => {
    expect(formatAgo('2026-10-15T11:58:00Z', now, 'en')).toBe('2 minutes ago')
    expect(formatAgo('2026-10-15T09:01:00Z', now, 'en')).toBe('2 hours ago')
    expect(formatAgo('2026-10-14T11:00:00Z', now, 'en')).toBe('1 day ago')
    expect(formatAgo('2026-09-15T12:00:00Z', now, 'en')).toBe('30 days ago')
  })

  it('says an instant a little ahead of the browser’s clock as just now', () => {
    expect(formatAgo('2026-10-15T12:00:05Z', now, 'en')).toBe('just now')
  })
})
