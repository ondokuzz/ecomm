import { useEffect, useId, useRef, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { Link } from 'react-router'
import { useConversation, useNewConversation, useSendMessage } from '../api/help'
import { useAuthPending, useSignin } from '../auth/session'
import { type HelpMessage, messageLimit, referencePath, remainingCharacters } from '../domain/help'
import { ErrorMessage } from './Status'
import { Button } from './ui/Button'
import { buttonClass } from './ui/buttonClass'
import { Icon } from './ui/Icon'

/**
 * Help, in the header of every page: a button that opens the Support Assistant's panel beside the
 * page, or over it on narrow screens. The panel is a non-modal dialog, so the page stays usable
 * beside it; focus moves into it when it opens and back to Help when it closes, and Escape closes it.
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
        Help
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

  return (
    <div className="help-body">
      <header className="help-header">
        <h2 id={titleId}>Help</h2>
        {signedIn && (
          <Button
            size="sm"
            variant="ghost"
            loading={newConversation.isPending}
            onClick={() => newConversation.mutate()}
          >
            New conversation
          </Button>
        )}
        <Button variant="ghost" icon onClick={onClose} aria-label="Close help">
          <Icon name="close" size={20} />
        </Button>
      </header>
      {pending ? null : signedIn ? <HelpConversation newConversationError={newConversation.error} /> : <SignInToAsk />}
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

function HelpConversation({ newConversationError }: { newConversationError: unknown }) {
  const conversation = useConversation(true)
  const send = useSendMessage()
  const [text, setText] = useState('')
  const [announcement, setAnnouncement] = useState('')
  const box = useRef<HTMLTextAreaElement>(null)
  const end = useRef<HTMLLIElement>(null)
  const boxId = useId()
  const countId = useId()
  const messages = conversation.data?.messages ?? []

  // Focus waits in the text box: when Help opens, and again once a reply has come.
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
        setAnnouncement(`Help replied: ${reply.text}`)
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
          title="We couldn't open Help"
          retrying={conversation.isFetching}
          onRetry={() => conversation.refetch()}
        />
      ) : null}
      {send.error ? <ErrorMessage error={send.error} title="Your message wasn't answered" /> : null}
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

function MessageItem({ message }: { message: HelpMessage }) {
  const customer = message.role === 'CUSTOMER'
  return (
    <li className={`help-message ${customer ? 'help-customer' : 'help-assistant'}`}>
      <span className="visually-hidden">{customer ? 'You: ' : 'Help: '}</span>
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
