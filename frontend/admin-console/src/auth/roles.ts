/** The realm role that opens the Admin Console. */
const staffRole = 'STAFF'

/**
 * Whether an access token carries the `STAFF` realm role, in Keycloak's `realm_access.roles`. This
 * only decides what the console shows: the token's signature isn't checked here, and the services
 * check it and the role on every request.
 */
export function isStaff(accessToken: string | undefined): boolean {
  const payload = accessToken?.split('.')[1]
  if (!payload) return false
  try {
    const claims = JSON.parse(decodeBase64Url(payload)) as { realm_access?: { roles?: unknown } }
    const roles = claims.realm_access?.roles
    return Array.isArray(roles) && roles.includes(staffRole)
  } catch {
    return false
  }
}

function decodeBase64Url(text: string): string {
  const base64 = text.replaceAll('-', '+').replaceAll('_', '/')
  const bytes = Uint8Array.from(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')), (c) => c.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}
