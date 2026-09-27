/** An RFC 7807 problem detail, as every service reports errors. Extra properties vary by endpoint. */
export interface Problem {
  title?: string
  detail?: string
  status?: number
  [property: string]: unknown
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem

  constructor(status: number, problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`)
    this.status = status
    this.problem = problem
  }
}

/** The services, each reached through the dev proxy or nginx at `/api/<service>`. */
export type Service = 'catalog' | 'inventory' | 'cart' | 'checkout-pricing' | 'order-management'

interface RequestOptions {
  method?: string
  body?: unknown
  /** The Customer's access token, for endpoints that need one. */
  token?: string
}

/** Calls a service's endpoint and returns its JSON body, or undefined for an empty one. */
export async function api<T>(service: Service, path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.token) headers.Authorization = `Bearer ${options.token}`
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  const response = await fetch(`/api/${service}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  // Services answer JSON or problem+json; nginx's own error pages (a service down) are HTML.
  const isJson = /json/.test(response.headers.get('Content-Type') ?? '')
  const text = await response.text()
  const json = isJson && text ? JSON.parse(text) : undefined
  if (!response.ok) throw new ApiError(response.status, typeof json === 'object' && json ? json : {})
  return json as T
}
