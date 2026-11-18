import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { AuthContext, type AuthContextProps } from 'react-oidc-context'
import { MemoryRouter } from 'react-router'
import { afterEach, vi } from 'vitest'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

/** A request the page sent, as the gateway would see it: method, path under `/api`, and JSON body. */
export interface SentRequest {
  method: string
  path: string
  body: unknown
}

/** A service's answer: its status, 200 by default, and its JSON body. */
export interface Answer {
  status?: number
  body?: unknown
}

/**
 * How the services answer one endpoint: `METHOD /api/<service>/<path>`, matched exactly, and the
 * answer, or a function of the request for one that depends on what was sent or how often.
 */
export type Endpoints = Record<string, Answer | ((request: SentRequest) => Answer)>

/**
 * Answers the page's requests as the services would, through `fetch`, the one outside party a
 * component test stubs; any endpoint not listed answers 404. Returns every request sent, in order.
 */
export function stubServices(endpoints: Endpoints): SentRequest[] {
  const sent: SentRequest[] = []
  vi.stubGlobal('fetch', async (path: string, init?: RequestInit) => {
    const request = {
      method: init?.method ?? 'GET',
      path,
      body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
    }
    sent.push(request)
    const endpoint = endpoints[`${request.method} ${path}`]
    const answer = typeof endpoint === 'function' ? endpoint(request) : (endpoint ?? notFound)
    const status = answer.status ?? 200
    return new Response(answer.body === undefined ? null : JSON.stringify(answer.body), {
      status,
      headers: { 'Content-Type': status < 400 ? 'application/json' : 'application/problem+json' },
    })
  })
  return sent
}

const notFound: Answer = { status: 404, body: { title: 'Not Found', status: 404 } }

/** Renders `page` at `path` as the app does for a signed-in Customer, with a fresh query cache. */
export function renderSignedIn(page: ReactNode, path = '/') {
  const queries = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const auth = { user: { access_token: 'customer-token' }, isAuthenticated: true, isLoading: false }
  return render(
    <AuthContext.Provider value={auth as unknown as AuthContextProps}>
      <QueryClientProvider client={queries}>
        <MemoryRouter initialEntries={[path]}>{page}</MemoryRouter>
      </QueryClientProvider>
    </AuthContext.Provider>,
  )
}
