import { describe, expect, it } from 'vitest'
import type { ModeratedReview } from './review'
import { actionsOf, historyRowsOf, openReportsLabel, removalOf } from './review'

const noReports = { SPAM: 0, OFFENSIVE: 0, OFF_TOPIC: 0, OTHER: 0 }

function review(changes: Partial<ModeratedReview>): Pick<ModeratedReview, 'status' | 'openReports'> {
  return { status: 'PUBLISHED', openReports: noReports, ...changes }
}

describe('openReportsLabel', () => {
  it('counts the open reports of each reason, most first, leaving out reasons with none', () => {
    expect(openReportsLabel({ SPAM: 1, OFFENSIVE: 3, OFF_TOPIC: 0, OTHER: 1 })).toBe('3 offensive · 1 spam · 1 other')
    expect(openReportsLabel({ ...noReports, OFF_TOPIC: 2 })).toBe('2 off-topic')
  })

  it('says none without any', () => {
    expect(openReportsLabel(noReports)).toBe('None')
  })
})

describe('actionsOf', () => {
  it('lets Staff publish or remove a held review', () => {
    expect(actionsOf(review({ status: 'HELD' }))).toEqual(['publish', 'remove'])
  })

  it('lets Staff dismiss the reports of a published review that has some open, or remove it', () => {
    expect(actionsOf(review({ openReports: { ...noReports, SPAM: 1 } }))).toEqual(['dismiss-reports', 'remove'])
    expect(actionsOf(review({}))).toEqual(['remove'])
  })

  it('leaves nothing to do to a removed review', () => {
    expect(actionsOf(review({ status: 'REMOVED' }))).toEqual([])
  })
})

describe('removalOf', () => {
  it('needs a reason, trimmed, of up to 1,000 characters', () => {
    expect(removalOf('  Insults the seller. ')).toEqual({ body: { reason: 'Insults the seller.' } })
    expect(removalOf('   ')).toEqual({ error: 'Say why it is removed: its author is told.' })
    expect(removalOf('r'.repeat(1001))).toEqual({ error: 'Keep it to 1,000 characters.' })
    expect(removalOf('r'.repeat(1000))).toEqual({ body: { reason: 'r'.repeat(1000) } })
  })
})

describe('historyRowsOf', () => {
  it('lists each action, newest first, with who took it, when, and its reason', () => {
    const rows = historyRowsOf(
      {
        history: [
          { action: 'REPORTS_DISMISSED', staffId: 'staff-1', at: '2026-10-15T02:00:00Z', reason: null },
          { action: 'REMOVED', staffId: 'staff-2', at: '2026-10-16T03:00:00Z', reason: 'Abusive' },
        ],
      },
      'en-GB',
    )
    expect(rows).toEqual([
      {
        action: 'Removed',
        staffId: 'staff-2',
        at: '2026-10-16T03:00:00Z',
        time: '16 Oct 2026, 12:00:00',
        reason: 'Abusive',
      },
      {
        action: 'Reports dismissed',
        staffId: 'staff-1',
        at: '2026-10-15T02:00:00Z',
        time: '15 Oct 2026, 11:00:00',
        reason: null,
      },
    ])
  })
})
