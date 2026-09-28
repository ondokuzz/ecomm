import { Icon } from './ui/Icon'
import { cx } from './ui/cx'

const steps = ['Cart', 'Payment', 'Done'] as const

export type CheckoutStep = (typeof steps)[number]

/** Where the Customer is on the way from Cart to Order: Cart → Payment → Done. */
export function CheckoutSteps({ current }: { current: CheckoutStep }) {
  const currentIndex = steps.indexOf(current)
  return (
    <nav aria-label="Checkout steps">
      <ol className="steps">
        {steps.map((step, i) => {
          const done = i < currentIndex
          return (
            <li
              key={step}
              className={cx('step', done && 'step-done', i === currentIndex && 'step-current')}
              aria-current={i === currentIndex ? 'step' : undefined}
            >
              <span className="step-marker" aria-hidden="true">
                {done ? <Icon name="check" size={14} /> : i + 1}
              </span>
              <span>
                {step}
                {done && <span className="visually-hidden"> (done)</span>}
              </span>
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
