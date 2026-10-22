import { describe, expect, it } from 'vitest'
import { channelLabel, messageTypeLabel, subjectLabel } from './template'

describe('messageTypeLabel', () => {
  it('reads a Message type as words', () => {
    expect(messageTypeLabel('ORDER_CONFIRMED')).toBe('Order confirmed')
    expect(messageTypeLabel('RMA_APPROVED')).toBe('RMA approved')
  })
})

describe('channelLabel', () => {
  it('names each Notification channel', () => {
    expect(channelLabel('EMAIL')).toBe('Email')
    expect(channelLabel('SMS')).toBe('SMS')
    expect(channelLabel('PUSH')).toBe('Push')
  })
})

describe('subjectLabel', () => {
  it("calls an email's subject a subject and a push's a title", () => {
    expect(subjectLabel('EMAIL')).toBe('Subject')
    expect(subjectLabel('PUSH')).toBe('Title')
  })
})
