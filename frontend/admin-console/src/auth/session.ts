import { createContext, useContext } from 'react'
import { hasAuthParams, useAuth } from 'react-oidc-context'
import { useLocation } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'

/** Where to go back to once Keycloak redirects here, carried through the sign-in as its `state`. */
export interface SigninState {
  returnTo: string
}

/** Whether the sign-in state is known yet; `AdminConsoleAuthProvider` provides it. */
export const SessionKnown = createContext(false)

/** True while the sign-in state isn't known yet: until the session is restored, or during a sign-in callback. */
export function useAuthPending(): boolean {
  return !useContext(SessionKnown) || hasAuthParams()
}

/** Sign-in happens on Keycloak's hosted page, which brings Staff back to the page they asked for. */
export function useSignin() {
  const auth = useAuth()
  const location = useLocation()
  const state: SigninState = { returnTo: location.pathname + location.search }
  return () => auth.signinRedirect({ state })
}

export function useSignout() {
  const auth = useAuth()
  const queryClient = useQueryClient()
  return async () => {
    queryClient.clear()
    await auth.signoutRedirect()
  }
}
