/** The ID-token claims the Storefront shows a signed-in Customer by. */
export interface CustomerProfile {
  given_name?: string
  family_name?: string
  name?: string
  email?: string
  preferred_username?: string
}

/** Up to two letters for the Customer's avatar: from their names, else their email or username. */
export function customerInitials(profile: CustomerProfile): string {
  const given = profile.given_name?.trim()
  const family = profile.family_name?.trim()
  if (given || family) return initials([given, family])

  const name = profile.name?.trim()
  if (name) {
    const words = name.split(/\s+/)
    return initials([words[0], words.length > 1 ? words[words.length - 1] : undefined])
  }

  const handle = (profile.email?.split('@')[0] ?? profile.preferred_username)?.trim()
  if (handle) {
    const parts = handle.split(/[._-]+/).filter(Boolean)
    return initials([parts[0], parts.length > 1 ? parts[parts.length - 1] : undefined])
  }
  return '?'
}

/** How the Storefront names the signed-in Customer. */
export function customerLabel(profile: CustomerProfile): string {
  return profile.email ?? profile.preferred_username ?? 'Your account'
}

function initials(words: (string | undefined)[]): string {
  return words
    .filter((word): word is string => Boolean(word))
    .map((word) => word[0].toUpperCase())
    .join('')
}
