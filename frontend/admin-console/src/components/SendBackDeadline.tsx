import { sendBackOf } from '../domain/rma'
import { Badge } from './ui/Badge'

/**
 * An approved RMA's Send-back deadline as of `now`, flagged while its units are awaited and it is
 * within 2 days or passed.
 */
export function SendBackDeadline({ sendBackBy, now, awaited }: { sendBackBy: string; now: Date; awaited: boolean }) {
  const view = sendBackOf(sendBackBy, now)
  return (
    <>
      <time dateTime={view.at}>{view.time}</time>
      {awaited && view.flag && (
        <>
          {' '}
          <Badge tone={view.flag === 'Passed' ? 'danger' : 'warning'}>{view.flag}</Badge>
        </>
      )}
    </>
  )
}
