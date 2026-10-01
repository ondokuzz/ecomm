import { type ReactNode, useEffect, useRef, useState } from 'react'
import { AuthProvider, hasAuthParams, useAuth } from 'react-oidc-context'
import { useLocation, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import type { User } from 'oidc-client-ts'
import { isUnauthorized } from '../api/failure'
import { Button } from '../components/ui/Button'
import { oidcSettings } from './oidc'
import { isStaff } from './roles'
import { SessionKnown, type SigninState, useAuthPending, useSignin, useSignout } from './session'

/** Wraps the app in oidc-client-ts, returning to the page Staff started from after sign-in. */
export function AdminConsoleAuthProvider({ children }: { children: ReactNode }) {
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
 * hidden iframe. Without a session this fails quietly, and `RequireStaff` sends the user to sign
 * in. Until the attempt settles, whether anyone is signed in isn't known.
 */
function RestoreSession({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [restoring, setRestoring] = useState(true)
  const started = useRef(false)

  // A failed sign-in (cancelled on Keycloak, or a used code) leaves Keycloak's parameters in the
  // URL; drop them so the app stops waiting for a callback that won't finish.
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
 * A 401 in the middle of a flow means the sign-in expired and silent renew couldn't refresh it, say
 * because the Keycloak session ended. Whichever request it was, send the user to sign in again and
 * bring them back to the page they were on.
 */
function SigninOnExpiry() {
  const queryClient = useQueryClient()
  const signin = useSignin()
  const latestSignin = useRef(signin)
  const redirecting = useRef(false)
  useEffect(() => {
    latestSignin.current = signin
  })

  useEffect(() => {
    const onError = (error: unknown) => {
      if (!isUnauthorized(error) || redirecting.current) return
      redirecting.current = true
      // If Keycloak can't be reached, the page's error stays and a later 401 tries again.
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

/**
 * Renders its children for a signed-in Staff member only. Anyone signed out goes to Keycloak to
 * sign in; anyone signed in without `STAFF`, such as a Customer, is told the console isn't for
 * them and may sign out. The services check the role on every request regardless.
 */
export function RequireStaff({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const pending = useAuthPending()
  const signin = useSignin()
  const redirecting = useRef(false)
  // Only a failed sign-in callback stops the redirect; a failed silent restore just means signed out.
  const signinFailed = auth.error?.source === 'signinCallback'

  useEffect(() => {
    // After a failed sign-in, let the user choose to try again rather than loop back to Keycloak.
    if (pending || auth.isAuthenticated || signinFailed || redirecting.current) return
    redirecting.current = true
    signin()
  }, [pending, auth.isAuthenticated, signinFailed, signin])

  if (signinFailed && !auth.isAuthenticated) {
    return (
      <Gate title="Sign-in didn't complete">
        <Button variant="primary" onClick={signin}>
          Try again
        </Button>
      </Gate>
    )
  }
  if (!auth.isAuthenticated) return <Gate title="Taking you to sign in…" />
  if (!isStaff(auth.user?.access_token)) return <NotStaff />
  return children
}

function NotStaff() {
  const auth = useAuth()
  const signout = useSignout()
  const [signingOut, setSigningOut] = useState(false)
  const email = auth.user?.profile.email
  return (
    <Gate title="This console is for Staff">
      <p className="muted">
        {email ? (
          <>
            You're signed in as <strong>{email}</strong>, which isn't a Staff account.
          </>
        ) : (
          "You're signed in with an account that isn't a Staff account."
        )}{' '}
        Sign out, then sign in with a Staff account.
      </p>
      <Button
        variant="primary"
        loading={signingOut}
        onClick={() => {
          setSigningOut(true)
          signout().catch(() => setSigningOut(false))
        }}
      >
        Sign out
      </Button>
    </Gate>
  )
}

/** A centred card in place of the console, for whoever can't use it yet. */
function Gate({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <main className="gate">
      <div className="gate-card">
        <span className="brand">
          <span className="brand-mark" aria-hidden="true">
            e
          </span>
          Ecomm Admin
        </span>
        <h1>{title}</h1>
        {children}
      </div>
    </main>
  )
}
