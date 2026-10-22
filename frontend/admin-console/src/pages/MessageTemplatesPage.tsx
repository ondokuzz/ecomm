import { useTemplatePreviews } from '../api/templates'
import { ErrorMessage } from '../components/Status'
import { Badge } from '../components/ui/Badge'
import { type TemplatePreview, channelLabel, messageTypeLabel, subjectLabel } from '../domain/template'

/**
 * `/message-templates`: every Message type on each Notification channel it goes on, as a Customer would get it, rendered
 * with made-up sample content. Read only: the templates live in the Notifications service.
 */
export function MessageTemplatesPage() {
  const previews = useTemplatePreviews()

  return (
    <>
      <div className="page-heading">
        <h1>Message templates</h1>
      </div>
      <p className="muted page-intro">
        What Customers are sent about their Orders, on each channel, shown with sample data for a made-up Customer. The
        wording lives in the Notifications service's templates and can't be edited here.
      </p>

      {previews.error ? (
        <ErrorMessage
          error={previews.error}
          title="Couldn't load the Message templates"
          retrying={previews.isFetching}
          onRetry={() => previews.refetch()}
        />
      ) : previews.data ? (
        previews.data.map((preview) => <Preview key={`${preview.messageType}-${preview.channel}`} preview={preview} />)
      ) : (
        <p className="muted" role="status">
          Loading the Message templates…
        </p>
      )}
    </>
  )
}

function Preview({ preview }: { preview: TemplatePreview }) {
  const id = `template-${preview.messageType}-${preview.channel}`.toLowerCase()
  const name = messageTypeLabel(preview.messageType)
  return (
    <section className="panel template-preview" aria-label={`${name}: ${channelLabel(preview.channel)}`}>
      <div className="panel-heading">
        <h2 id={id}>{name}</h2>
        <Badge tone="primary">{channelLabel(preview.channel)}</Badge>
        <code>{preview.messageType}</code>
        <span className="muted">{preview.locale}</span>
      </div>
      {preview.subject != null && (
        <p>
          <strong>{subjectLabel(preview.channel)}:</strong> {preview.subject}
        </p>
      )}
      {preview.html != null && (
        <iframe
          title={`${name}: ${channelLabel(preview.channel)} as HTML`}
          srcDoc={preview.html}
          sandbox=""
          className="template-html"
        />
      )}
      {preview.text != null &&
        (preview.html != null ? (
          <details>
            <summary>Plain text</summary>
            <pre className="template-text">{preview.text}</pre>
          </details>
        ) : (
          <pre className="template-text">{preview.text}</pre>
        ))}
    </section>
  )
}
