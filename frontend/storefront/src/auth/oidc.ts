import { InMemoryWebStorage, type UserManagerSettings, WebStorageStateStore } from 'oidc-client-ts'

const origin = window.location.origin

/** Where oidc-client-ts finishes a sign-in in a hidden iframe; `main.tsx` answers it without rendering the app. */
export const silentRedirectPath = '/silent-renew'

/**
 * Authorization Code + PKCE against the realm's public `storefront` client. Tokens live in memory
 * only, never in web storage: a reload drops them, and the app gets them back silently from the
 * Keycloak session. Silent renew refreshes them before the access token expires.
 */
export const oidcSettings: UserManagerSettings = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8180/realms/ecomm',
  client_id: 'storefront',
  redirect_uri: `${origin}/`,
  silent_redirect_uri: `${origin}${silentRedirectPath}`,
  post_logout_redirect_uri: `${origin}/`,
  response_type: 'code',
  scope: 'openid',
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
}
