/**
 * The Keycloak realm to sign in against. The same image runs in every Environment, so it is read
 * as the page loads, from the `oidc-authority` meta tag nginx fills in from its `OIDC_AUTHORITY`
 * (nginx.conf), not built in. Vite's dev server leaves the tag empty, which means the local stack's.
 */
export function authority(named: string | undefined): string {
  return named || 'http://localhost:8180/realms/ecomm'
}
