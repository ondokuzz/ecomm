/** An RFC 7807 problem detail, as every service reports errors. Extra properties vary by endpoint. */
export interface Problem {
  title?: string
  detail?: string
  status?: number
  /** The request's Correlation ID, which the Customer sees as the error's support reference. */
  correlationId?: unknown
  [property: string]: unknown
}

/** The header that carries a request's Correlation ID, on the request and on its response. */
const correlationIdHeader = 'X-Correlation-Id'

/** A service's error answer, with the Correlation ID that names the request in its logs, when it has one. */
export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem
  readonly correlationId: string | undefined

  constructor(status: number, problem: Problem, headerCorrelationId?: string) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`)
    this.status = status
    this.problem = problem
    this.correlationId =
      typeof problem.correlationId === 'string' ? problem.correlationId : headerCorrelationId
  }
}

/** A request that got no answer at all: the network, or the browser, stopped it. */
export class NetworkError extends Error {
  constructor(cause: unknown) {
    super('The request got no answer', { cause })
  }
}

/** The services, each reached through the dev proxy or nginx at `/api/<service>`. */
export type Service = 'catalog' | 'inventory' | 'cart' | 'checkout-pricing' | 'order-management' | 'search-discovery'

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
  }).catch((cause: unknown) => {
    throw new NetworkError(cause)
  })
  if (!response.ok) throw await errorFrom(response)
  const text = await response.text()
  return (text && isJson(response) ? JSON.parse(text) : undefined) as T
}

/**
 * The error a failed response stands for. Its reference is the problem detail's `correlationId`, or
 * the response header's when the body has none, as with a web server's own error page.
 */
export async function errorFrom(response: Response): Promise<ApiError> {
  const text = await response.text()
  let json: unknown
  try {
    json = text && isJson(response) ? JSON.parse(text) : undefined
  } catch {
    json = undefined
  }
  const problem = typeof json === 'object' && json ? (json as Problem) : {}
  return new ApiError(response.status, problem, response.headers.get(correlationIdHeader) ?? undefined)
}

// Services answer JSON or problem+json; nginx's own error pages (the gateway down) are HTML.
function isJson(response: Response): boolean {
  return /json/.test(response.headers.get('Content-Type') ?? '')
}
