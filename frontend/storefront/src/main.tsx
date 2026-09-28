import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { UserManager } from 'oidc-client-ts'
import { App } from './App'
import { oidcSettings, silentRedirectPath } from './auth/oidc'
import '@fontsource-variable/bricolage-grotesque'
import '@fontsource-variable/inter'
import './index.css'

if (window.location.pathname === silentRedirectPath) {
  // Loaded in the hidden iframe of a silent sign-in: hand the result to the parent window only.
  new UserManager(oidcSettings).signinSilentCallback()
} else {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
}
