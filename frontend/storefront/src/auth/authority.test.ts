import { describe, expect, it } from 'vitest'
import { authority } from './authority'

describe('the Keycloak realm signed in against', () => {
  it('is the one the page names, as nginx writes it in an Environment', () => {
    expect(authority('https://auth.shop.example.com/realms/ecomm')).toBe('https://auth.shop.example.com/realms/ecomm')
  })

  it("is the local stack's when the page names none, as under Vite's dev server", () => {
    expect(authority('')).toBe('http://localhost:8180/realms/ecomm')
    expect(authority(undefined)).toBe('http://localhost:8180/realms/ecomm')
  })
})
