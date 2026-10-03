import { NavLink } from 'react-router'

/** Moves between Promotions' Coupons, which take a code, and its Campaigns, which don't. */
export function PromotionsNav() {
  return (
    <nav className="subnav" aria-label="Promotions">
      <NavLink to="/promotions/coupons" className="subnav-link">
        Coupons
      </NavLink>
      <NavLink to="/promotions/campaigns" className="subnav-link">
        Campaigns
      </NavLink>
    </nav>
  )
}
