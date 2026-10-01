import type { ReactNode } from 'react'
import { CheckoutSteps } from './CheckoutSteps'
import { Card } from './ui/Card'
import { Skeleton } from './ui/Skeleton'

/*
 * Each page's shape while its data loads, in the page's own layout classes, so nothing jumps when
 * the content arrives. Screen readers hear "Loading…" once, from the status region.
 */

function PageLoading({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <section className={className} role="status" aria-busy="true">
      <span className="visually-hidden">Loading…</span>
      {children}
    </section>
  )
}

function HeadingSkeleton({ width = '14rem' }: { width?: string }) {
  return <Skeleton width={width} height="2.5rem" radius="var(--radius-md)" className="heading-skeleton" />
}

function LineSkeletons({ count, compact }: { count: number; compact?: boolean }) {
  return (
    <ul className={compact ? 'cart-lines compact' : 'cart-lines'}>
      {Array.from({ length: count }, (_, i) => (
        <li key={i} className="cart-line">
          <Skeleton height={compact ? '2.625rem' : '4.5rem'} radius="var(--radius-md)" className="cart-line-thumb" />
          <div className="cart-line-info">
            <Skeleton width="70%" height="1.25rem" />
            <Skeleton width="35%" height="0.875rem" />
          </div>
          <Skeleton width="5rem" height="1.25rem" className="cart-line-total" />
        </li>
      ))}
    </ul>
  )
}

function SummarySkeleton({ rows = 3, children }: { rows?: number; children?: ReactNode }) {
  return (
    <Card className="order-summary">
      <Skeleton width="60%" height="1.5rem" />
      {children}
      <div className="skeleton-rows">
        {Array.from({ length: rows }, (_, i) => (
          <Skeleton key={i} height="1.25rem" />
        ))}
      </div>
      <Skeleton height="3rem" radius="var(--radius-pill)" />
    </Card>
  )
}

export function CartSkeleton() {
  return (
    <PageLoading>
      <div className="page-heading">
        <HeadingSkeleton />
      </div>
      <div className="cart-layout">
        <div className="cart-main">
          <LineSkeletons count={3} />
        </div>
        <SummarySkeleton />
      </div>
    </PageLoading>
  )
}

export function CheckoutSkeleton() {
  return (
    <PageLoading>
      <CheckoutSteps current="Payment" />
      <HeadingSkeleton width="10rem" />
      <Skeleton height="3rem" radius="var(--radius-md)" className="heading-skeleton" />
      <div className="cart-layout">
        <div className="cart-main">
          <Card className="payment">
            <Skeleton width="30%" height="1.5rem" />
            <Skeleton height="12rem" radius="var(--radius-lg)" className="mock-card-skeleton" />
            <div className="skeleton-rows">
              {Array.from({ length: 4 }, (_, i) => (
                <Skeleton key={i} height="3rem" radius="var(--radius-md)" />
              ))}
            </div>
            <Skeleton height="3rem" radius="var(--radius-pill)" />
          </Card>
        </div>
        <SummarySkeleton rows={4}>
          <LineSkeletons count={2} compact />
        </SummarySkeleton>
      </div>
    </PageLoading>
  )
}

export function OrdersSkeleton() {
  return (
    <PageLoading>
      <HeadingSkeleton width="12rem" />
      <ul className="order-cards">
        {Array.from({ length: 4 }, (_, i) => (
          <li key={i}>
            <Card className="order-card">
              <div className="order-card-top">
                <div className="skeleton-rows">
                  <Skeleton width="9rem" height="1.5rem" />
                  <Skeleton width="6rem" height="0.875rem" />
                </div>
                <Skeleton width="5rem" height="1.5rem" radius="var(--radius-pill)" />
              </div>
              <div className="order-card-thumbs">
                {Array.from({ length: 3 }, (_, j) => (
                  <Skeleton key={j} width="4.5rem" height="3.375rem" radius="var(--radius-md)" />
                ))}
              </div>
              <div className="order-card-bottom">
                <Skeleton width="4rem" height="1rem" />
                <Skeleton width="5rem" height="1.25rem" />
              </div>
            </Card>
          </li>
        ))}
      </ul>
    </PageLoading>
  )
}

export function OrderSkeleton() {
  return (
    <PageLoading>
      <Skeleton width="8rem" height="1rem" className="heading-skeleton" />
      <div className="page-heading">
        <HeadingSkeleton width="16rem" />
      </div>
      <div className="cart-layout">
        <div className="cart-main stack">
          <Card>
            <Skeleton width="40%" height="1.5rem" />
            <Skeleton height="4rem" radius="var(--radius-md)" className="mock-card-skeleton" />
          </Card>
          <Card>
            <Skeleton width="30%" height="1.5rem" />
            <LineSkeletons count={2} compact />
          </Card>
        </div>
        <SummarySkeleton rows={5} />
      </div>
    </PageLoading>
  )
}
