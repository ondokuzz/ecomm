import { readFileSync } from 'node:fs'

/**
 * One of the stack's secrets, as `make secrets` wrote it for the stack's Compose project (the root
 * README's "Secrets"): the default project's, or `COMPOSE_PROJECT_NAME`'s.
 */
export function stackSecret(name: string): string {
  const project = process.env.COMPOSE_PROJECT_NAME ?? 'ecomm'
  return readFileSync(new URL(`../../../.secrets/${project}/${name}`, import.meta.url), 'utf8').trim()
}
