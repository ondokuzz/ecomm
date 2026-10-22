import { type FormEvent, useId, useState } from 'react'
import { ApiError } from '../api/http'
import { usePreferences, useSavePreferences } from '../api/preferences'
import { ErrorMessage, ErrorState } from '../components/Status'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Input } from '../components/ui/Input'
import { Skeleton } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/toasts'
import {
  type Preferences,
  type PreferencesForm,
  preferencesErrorsOf,
  preferencesFormOf,
  preferencesRequestOf,
} from '../domain/preferences'

/**
 * `/notification-preferences`: how the Customer hears about their Orders. Email always; text messages
 * to a mobile number, and push, when they turn them on, for their Orders shipping and arriving,
 * replacements shipping, and Refunds.
 */
export function NotificationPreferencesPage() {
  const preferences = usePreferences()

  if (preferences.error) {
    return (
      <ErrorState
        title="We couldn't load your notification preferences"
        error={preferences.error}
        retrying={preferences.isFetching}
        onRetry={() => preferences.refetch()}
      />
    )
  }
  return (
    <section className="preferences-page">
      <h1>Notification preferences</h1>
      <p className="muted">
        We email you about your orders, payments and returns. You can also get a text message or a push notification
        when an order ships or arrives, a replacement ships, or you're refunded.
      </p>
      {preferences.data ? (
        // Keyed by when they were saved, so the form starts again from what was saved.
        <PreferencesEditor key={preferences.data.updatedAt ?? 'never'} saved={preferences.data} />
      ) : (
        <Card aria-busy="true">
          <Skeleton width="60%" height="1.5rem" />
          <Skeleton width="80%" height="2.5rem" />
          <span className="visually-hidden">Loading…</span>
        </Card>
      )}
    </section>
  )
}

function PreferencesEditor({ saved }: { saved: Preferences }) {
  const [form, setForm] = useState<PreferencesForm>(() => preferencesFormOf(saved))
  const [showErrors, setShowErrors] = useState(false)
  const save = useSavePreferences()
  const toast = useToast()
  const id = useId()
  const update = (change: Partial<PreferencesForm>) => {
    setForm((current) => ({ ...current, ...change }))
    save.reset()
  }

  const refused = save.error instanceof ApiError && save.error.problem.reason === 'invalidPhoneNumber'
  const errors = showErrors ? preferencesErrorsOf(form) : {}
  const phoneError = errors.phoneNumber ?? (refused ? save.error?.message : undefined)

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setShowErrors(true)
    if (preferencesErrorsOf(form).phoneNumber) return
    save.mutate(preferencesRequestOf(form), {
      onSuccess: () => toast({ message: 'Notification preferences saved' }),
    })
  }

  return (
    <Card>
      <form className="preferences-form" aria-label="Notification preferences" onSubmit={submit} noValidate>
        <label className="preference">
          <input type="checkbox" checked disabled />
          <span>
            <strong>Email</strong>
            <small className="muted">Always on: every message is about your own orders.</small>
          </span>
        </label>

        <label className="preference">
          <input type="checkbox" checked={form.sms} onChange={(event) => update({ sms: event.target.checked })} />
          <span>
            <strong>Text messages (SMS)</strong>
            <small className="muted">To your mobile number.</small>
          </span>
        </label>
        <label className="field">
          <span>Mobile number</span>
          <Input
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            placeholder="+49 151 12345678"
            value={form.phoneNumber}
            onChange={(event) => update({ phoneNumber: event.target.value })}
            aria-invalid={phoneError ? true : undefined}
            aria-describedby={phoneError ? `${id}-phone` : `${id}-phone-hint`}
          />
          <small id={`${id}-phone-hint`} className="muted">
            In international form: a plus and the country code first.
          </small>
        </label>
        {phoneError && (
          <span id={`${id}-phone`} className="field-error" role="alert">
            {phoneError}
          </span>
        )}

        <label className="preference">
          <input type="checkbox" checked={form.push} onChange={(event) => update({ push: event.target.checked })} />
          <span>
            <strong>Push notifications</strong>
            <small className="muted">On your devices.</small>
          </span>
        </label>

        {save.error && !refused && <ErrorMessage error={save.error} title="We couldn't save your preferences" />}
        <div>
          <Button type="submit" variant="primary" loading={save.isPending}>
            Save
          </Button>
        </div>
      </form>
    </Card>
  )
}
