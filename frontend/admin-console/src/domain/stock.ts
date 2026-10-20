import { formatTime } from './order'
import type { Page } from './paging'

/**
 * What a Stock movement records: Staff setting On-hand, a Reservation made, released or committed,
 * or units arriving from a supplier or a return. `RECEIVED` is unused until replenishment exists.
 */
export type StockMovementKind = 'ADJUSTED' | 'RESERVED' | 'RELEASED' | 'COMMITTED' | 'RECEIVED' | 'RESTOCKED'

/**
 * One change to a Variant's On-hand units, or to what Reservations hold of them, recorded for
 * good. `reservationId` names the Reservation that made it, `reason` is Staff's, and `reference`
 * is what it was for: a restock's RMA reference, or `rma:<rmaId>` for the movements of the
 * Reservation an RMA's replacement is held with. Each is null otherwise.
 */
export interface StockMovement {
  kind: StockMovementKind
  onHandChange: number
  reservedChange: number
  reservationId: string | null
  reason: string | null
  reference: string | null
  at: string
}

/**
 * A page of a Variant's Stock movements, newest first, with its On-hand now, the sum of every
 * movement's `onHandChange`, and whether the two are equal.
 */
export interface StockMovementPage extends Page<StockMovement> {
  variantId: string
  onHand: number
  onHandFromMovements: number
  balanced: boolean
}

/** How many Stock movements a page shows. */
export const movementsPageSize = 20

/** One Stock movement as the movements page lists it. */
export interface MovementRow {
  kind: string
  /** Signed, as "+25", "−2" or "0". */
  onHandChange: string
  reservedChange: string
  /** The Reservation's ID, or a dash when Staff made it. */
  reservation: string
  /** Staff's reason, or a dash when none was given. */
  reason: string
  /** The RMA reference it was for, such as `RMA-3F2A9C1B`, or a dash. */
  reference: string
  /** The RMA to link the reference to, when Inventory names it by ID. */
  rmaId: string | null
  /** The instant, for `<time dateTime>`. */
  at: string
  /** When, in the Staff member's own time zone, to the second. */
  time: string
}

/** A change in units with its sign: "+25", "−2" (a minus sign, not a hyphen) or "0". */
function signed(change: number): string {
  return change > 0 ? `+${change}` : change < 0 ? `−${-change}` : '0'
}

/** How Inventory names an RMA that owns a Reservation: `rma:<rmaId>`. */
const rmaOwnerPrefix = 'rma:'

/**
 * A movement's reference as Staff read it: a restock's as given, and an RMA named by ID as its
 * RMA reference, `RMA-` and the start of its ID, upper-cased.
 */
function referenceOf(reference: string | null): Pick<MovementRow, 'reference' | 'rmaId'> {
  if (reference === null) return { reference: '—', rmaId: null }
  if (!reference.startsWith(rmaOwnerPrefix)) return { reference, rmaId: null }
  const rmaId = reference.slice(rmaOwnerPrefix.length)
  return { reference: `RMA-${rmaId.slice(0, 8).toUpperCase()}`, rmaId }
}

/** A Variant's Stock movements for Staff, in the order Inventory gives them. */
export function movementRowsOf(movements: StockMovement[], locale?: string): MovementRow[] {
  return movements.map((movement) => ({
    kind: movement.kind.charAt(0) + movement.kind.slice(1).toLowerCase(),
    onHandChange: signed(movement.onHandChange),
    reservedChange: signed(movement.reservedChange),
    reservation: movement.reservationId ?? '—',
    reason: movement.reason ?? '—',
    ...referenceOf(movement.reference),
    at: movement.at,
    time: formatTime(movement.at, locale),
  }))
}

/** Whether a Variant's On-hand is explained by its movements, as the movements page says it. */
export function balanceOf({
  onHand,
  onHandFromMovements,
  balanced,
}: Pick<StockMovementPage, 'onHand' | 'onHandFromMovements' | 'balanced'>): { label: string; detail: string } {
  return balanced
    ? { label: 'Balanced', detail: `On-hand, ${onHand}, equals the sum of every movement.` }
    : { label: 'Out of balance', detail: `On-hand is ${onHand}, but every movement adds up to ${onHandFromMovements}.` }
}
