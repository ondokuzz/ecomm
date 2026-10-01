/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** The Keycloak realm the Admin Console signs in against; defaults to the compose realm. */
  readonly VITE_OIDC_AUTHORITY?: string
}
