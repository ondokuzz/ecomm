import { type ReactNode, useEffect, useRef, useState } from 'react'
import { AuthProvider, hasAuthParams, useAuth } from 'react-oidc-context'
import { useLocation, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import type { User } from 'oidc-client-ts'
import { isUnauthorized } from '../api/failure'
import { oidcSettings } from './oidc'
import { SessionKnown, type SigninState, useAuthPending, useSignin } from './session'

/** Wraps the app in oidc-client-ts, returning to the page the Customer started from after sign-in. */
export function StorefrontAuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const onSigninCallback = (user: User | undefined) => {
    const state = user?.state as SigninState | undefined
    navigate(state?.returnTo ?? '/', { replace: true })
  }
  return (
    <AuthProvider {...oidcSettings} onSigninCallback={onSigninCallback}>
      <RestoreSession>
        <SigninOnExpiry />
        {children}
      </RestoreSession>
    </AuthProvider>
  )
}

/**
 * Tokens don't survive a reload, so on start try to get them back from the Keycloak session in a
 * hidden iframe. Without a session this fails quietly and the Customer stays signed out. Until the
 * attempt settles, whether the Customer is signed in isn't known.
 */
function RestoreSession({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [restoring, setRestoring] = useState(true)
  const started = useRef(false)

  // A failed sign-in (the Customer cancelled on Keycloak, or a used code) leaves Keycloak's
  // parameters in the URL; drop them so the app stops waiting for a callback that won't finish.
  const failedCallback = auth.error?.source === 'signinCallback' && hasAuthParams()
  useEffect(() => {
    if (failedCallback) navigate(location.pathname, { replace: true })
  }, [failedCallback, navigate, location.pathname])

  useEffect(() => {
    if (started.current || auth.isLoading) return
    started.current = true
    const attempt = auth.user || hasAuthParams() ? Promise.resolve() : auth.signinSilent().catch(() => undefined)
    attempt.finally(() => setRestoring(false))
  }, [auth])
  return <SessionKnown.Provider value={!auth.isLoading && !restoring}>{children}</SessionKnown.Provider>
}

/**
 * A 401 in the middle of a flow means the Customer's sign-in expired and silent renew couldn't
 * refresh it, say because their Keycloak session ended. Whichever request it was, a page's query or
 * an action, send them to sign in again and bring them back to the page they were on.
 */
function SigninOnExpiry() {
  const queryClient = useQueryClient()
  const { signin } = useSignin()
  const latestSignin = useRef(signin)
  const redirecting = useRef(false)
  useEffect(() => {
    latestSignin.current = signin
  })

  useEffect(() => {
    const onError = (error: unknown) => {
      if (!isUnauthorized(error) || redirecting.current) return
      redirecting.current = true
      // If Keycloak can't be reached, the page's error panel stays and a later 401 tries again.
      latestSignin.current().catch(() => {
        redirecting.current = false
      })
    }
    // Query and mutation cache events share this shape: an update whose action is the failure.
    const onEvent = (event: { type: string; action?: { type: string; error?: unknown } }) => {
      if (event.type === 'updated' && event.action?.type === 'error') onError(event.action.error)
    }
    const queries = queryClient.getQueryCache().subscribe(onEvent)
    const mutations = queryClient.getMutationCache().subscribe(onEvent)
    return () => {
      queries()
      mutations()
    }
  }, [queryClient])
  return null
}

/** Renders its children for a signed-in Customer, and sends anyone else to Keycloak to sign in. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const pending = useAuthPending()
  const { signin } = useSignin()
  const redirecting = useRef(false)
  // Only a failed sign-in callback stops the redirect; a failed silent restore just means signed out.
  const signinFailed = auth.error?.source === 'signinCallback'

  useEffect(() => {
    // After a failed sign-in, let the Customer choose to try again rather than loop back to Keycloak.
    if (pending || auth.isAuthenticated || signinFailed || redirecting.current) return
    redirecting.current = true
    signin()
  }, [pending, auth.isAuthenticated, signinFailed, signin])

  if (signinFailed && !auth.isAuthenticated) {
    return (
      <p>
        <span className="error">Sign-in didn't complete.</span> <button onClick={signin}>Try again</button>
      </p>
    )
  }

  if (auth.isAuthenticated) return children
  return <p className="muted">Taking you to sign in…</p>
}
