import { type ReactNode, useEffect, useId, useRef, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { Link } from 'react-router'
import { useConversation, useNewConversation, useSendMessage, useTurnRunning } from '../api/help'
import { ApiError } from '../api/http'
import { useAuthPending, useSignin } from '../auth/session'
import { type HelpMessage, askAgainAt, messageLimit, referencePath, remainingCharacters } from '../domain/help'
import { ErrorMessage, SupportReference } from './Status'
import { Button } from './ui/Button'
import { buttonClass } from './ui/buttonClass'
import { Icon } from './ui/Icon'

/**
 * Ecomm Agent, in the header of every page: a button that opens the Support Assistant's panel beside
 * the page, or over it on narrow screens. The panel is a non-modal dialog, so the page stays usable
 * beside it; focus moves into it when it opens and back to its button when it closes, and Escape
 * closes it.
 */
export function Help() {
  const [open, setOpen] = useState(false)
  const button = useRef<HTMLButtonElement>(null)
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  // The dialog opens before its body mounts, so the body's focus lands in a shown dialog.
  const show = () => {
    dialog.current?.show()
    setOpen(true)
  }

  const close = () => {
    dialog.current?.close()
    setOpen(false)
    button.current?.focus()
  }

  return (
    <>
      <button
        ref={button}
        type="button"
        className={buttonClass({ variant: 'ghost' }, 'help-toggle')}
        aria-expanded={open}
        aria-haspopup="dialog"
        onClick={() => (open ? close() : show())}
      >
        <Icon name="help" size={20} />
        Ecomm Agent
      </button>
      <dialog
        ref={dialog}
        className="help-panel"
        aria-labelledby={titleId}
        onKeyDown={(e) => {
          if (e.key === 'Escape') {
            e.preventDefault()
            close()
          }
        }}
      >
        {open && <HelpBody titleId={titleId} onClose={close} />}
      </dialog>
    </>
  )
}

function HelpBody({ titleId, onClose }: { titleId: string; onClose: () => void }) {
  const auth = useAuth()
  const pending = useAuthPending()
  const signedIn = !pending && auth.user !== null && auth.user !== undefined
  const newConversation = useNewConversation()
  // Ending the Conversation mid-turn would leave the turn's reply with nowhere to go.
  const turnRunning = useTurnRunning()

  return (
    <div className="help-body">
      <header className="help-header">
        <h2 id={titleId}>Ecomm Agent</h2>
        {signedIn && (
          <Button
            size="sm"
            variant="ghost"
            loading={newConversation.isPending}
            disabled={turnRunning}
            onClick={() => newConversation.mutate()}
          >
            New conversation
          </Button>
        )}
        <Button variant="ghost" icon onClick={onClose} aria-label="Close Ecomm Agent">
          <Icon name="close" size={20} />
        </Button>
      </header>
      {pending ? null : signedIn ? (
        <HelpConversation
          newConversationError={newConversation.error}
          onNewConversation={() => newConversation.mutate()}
        />
      ) : (
        <SignInToAsk />
      )}
    </div>
  )
}

function SignInToAsk() {
  const { signin } = useSignin()
  return (
    <div className="help-signed-out">
      <p>Sign in to ask about your Orders.</p>
      <Button variant="primary" autoFocus onClick={signin}>
        Log in
      </Button>
    </div>
  )
}

function HelpConversation({
  newConversationError,
  onNewConversation,
}: {
  newConversationError: unknown
  onNewConversation: () => void
}) {
  const conversation = useConversation(true)
  const send = useSendMessage()
  const [text, setText] = useState('')
  const [announcement, setAnnouncement] = useState('')
  const box = useRef<HTMLTextAreaElement>(null)
  const end = useRef<HTMLLIElement>(null)
  const boxId = useId()
  const countId = useId()
  const messages = conversation.data?.messages ?? []
  const conversationId = conversation.data?.id
  const resetSend = send.reset

  // A new Conversation clears why the last message wasn't answered; its text stays in the box.
  useEffect(() => resetSend(), [conversationId, resetSend])

  // Focus waits in the text box: when the panel opens, and again once a reply has come.
  useEffect(() => {
    if (!send.isPending) box.current?.focus()
  }, [send.isPending, conversation.isSuccess])

  useEffect(() => {
    end.current?.scrollIntoView?.({ block: 'end' })
  }, [messages.length, send.isPending])

  const submit = (e: { preventDefault: () => void }) => {
    e.preventDefault()
    const question = text.trim()
    if (!question || send.isPending) return
    // The text stays in the box until it has been answered, so a failure loses nothing.
    send.mutate(question, {
      onSuccess: ({ reply }) => {
        setText('')
        setAnnouncement(`Ecomm Agent replied: ${reply.text}`)
      },
    })
  }

  const remaining = remainingCharacters(text)
  return (
    <>
      <ol className="help-transcript" aria-label="Conversation">
        {messages.length === 0 && !conversation.isPending && (
          <li className="help-message help-assistant">Ask me about your Orders, for example "Where is my Order?"</li>
        )}
        {messages.map((message, i) => (
          <MessageItem key={i} message={message} />
        ))}
        {send.isPending && (
          <>
            <li className="help-message help-customer">{send.variables}</li>
            <li className="help-message help-assistant help-working" aria-hidden="true">
              <span className="spinner" /> Looking that up…
            </li>
          </>
        )}
        <li ref={end} className="help-end" aria-hidden="true" />
      </ol>
      {/* Replies are announced as they come, without taking the Customer's focus. */}
      <div className="visually-hidden" aria-live="polite">
        {send.isPending ? 'Looking that up…' : announcement}
      </div>
      {conversation.error ? (
        <ErrorMessage
          error={conversation.error}
          title="We couldn't open Ecomm Agent"
          retrying={conversation.isFetching}
          onRetry={() => conversation.refetch()}
        />
      ) : null}
      {send.error ? (
        <SendFailure
          error={send.error}
          onNewConversation={() => {
            send.reset()
            onNewConversation()
          }}
        />
      ) : null}
      {newConversationError ? (
        <ErrorMessage error={newConversationError} title="We couldn't start a new conversation" />
      ) : null}
      <form className="help-form" onSubmit={submit}>
        <label htmlFor={boxId} className="visually-hidden">
          Your question
        </label>
        <textarea
          ref={box}
          id={boxId}
          className="input help-input"
          rows={3}
          maxLength={messageLimit}
          placeholder="Ask about your Orders…"
          value={text}
          disabled={send.isPending || !conversation.data}
          aria-describedby={countId}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) submit(e)
          }}
        />
        <div className="help-form-actions">
          <small id={countId} className="muted">
            {remaining.toLocaleString('en')} of {messageLimit.toLocaleString('en')} characters left
          </small>
          <Button type="submit" variant="primary" size="sm" loading={send.isPending} disabled={!text.trim()}>
            Send
          </Button>
        </div>
      </form>
    </>
  )
}

const askAgainFormat = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' })

/**
 * Why the Customer's message wasn't answered, and the way on: past their message limits, the time
 * they can ask again; the assistant unavailable or busy, to try later, with the support reference; a
 * full Conversation, a new one. The message stays in the box for each.
 */
function SendFailure({ error, onNewConversation }: { error: unknown; onNewConversation: () => void }) {
  if (!(error instanceof ApiError)) return <ErrorMessage error={error} title="Your message wasn't answered" />
  const reason = error.problem.reason
  if (reason === 'assistantRateLimited') {
    const retryAfter = error.retryAfter
    const when = retryAfter === undefined ? 'later' : `at ${askAgainFormat.format(askAgainAt(retryAfter, new Date()))}`
    return <HelpAlert>{`You've sent a lot of messages. You can ask again ${when}.`}</HelpAlert>
  }
  if (reason === 'assistantUnavailable' || reason === 'assistantBusy') {
    return (
      <HelpAlert reference={error.correlationId}>
        Ecomm Agent is unavailable just now. Please try again later.
      </HelpAlert>
    )
  }
  if (reason === 'conversationFull') {
    return (
      <HelpAlert
        action={
          <Button size="sm" className="alert-action" onClick={onNewConversation}>
            Start a new conversation
          </Button>
        }
      >
        This conversation is full. Start a new one to ask more.
      </HelpAlert>
    )
  }
  return <ErrorMessage error={error} title="Your message wasn't answered" />
}

function HelpAlert({ children, reference, action }: { children: string; reference?: string; action?: ReactNode }) {
  return (
    <div className="alert alert-danger" role="alert">
      <Icon name="alert" size={18} />
      <div className="alert-body">
        <strong>Your message wasn't answered</strong>
        <span>{children}</span>
        {reference && <SupportReference reference={reference} />}
      </div>
      {action}
    </div>
  )
}

function MessageItem({ message }: { message: HelpMessage }) {
  const customer = message.role === 'CUSTOMER'
  return (
    <li className={`help-message ${customer ? 'help-customer' : 'help-assistant'}`}>
      <span className="visually-hidden">{customer ? 'You: ' : 'Ecomm Agent: '}</span>
      <p>{message.text}</p>
      {message.references.length > 0 && (
        <ul className="help-references" aria-label="Mentioned">
          {message.references.map((reference) => (
            <li key={`${reference.kind}-${reference.id}`}>
              <Link to={referencePath(reference)}>
                <Icon name="orders" size={16} />
                {reference.label}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}
