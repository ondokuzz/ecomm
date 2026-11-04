import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Conversation, HelpMessage } from '../domain/help'
import { isNotFound } from './failure'
import { api } from './http'

const helpKey = ['help']

/**
 * The Conversation's ID is kept for the browser tab, so Help reloads its transcript on any page
 * and after a reload. Storage may be refused, as in some private windows; Help then starts a new
 * Conversation each time it opens.
 */
const storageKey = 'ecomm.help.conversationId'

function storedConversationId(): string | undefined {
  try {
    return sessionStorage.getItem(storageKey) ?? undefined
  } catch {
    return undefined
  }
}

function storeConversationId(id: string | undefined) {
  try {
    if (id) sessionStorage.setItem(storageKey, id)
    else sessionStorage.removeItem(storageKey)
  } catch {
    // Not kept; the next page starts a new Conversation.
  }
}

async function startConversation(token: string | undefined): Promise<Conversation> {
  const conversation = await api<Conversation>('support-assistant', '/conversations', { method: 'POST', token })
  storeConversationId(conversation.id)
  return conversation
}

/** The kept Conversation, or a new one when there is none or it has ended or lapsed (a 404). */
async function currentConversation(token: string | undefined): Promise<Conversation> {
  const id = storedConversationId()
  if (!id) return startConversation(token)
  try {
    return await api<Conversation>('support-assistant', `/conversations/${encodeURIComponent(id)}`, { token })
  } catch (error) {
    if (isNotFound(error)) return startConversation(token)
    throw error
  }
}

/** The Customer's Conversation, loaded once Help first opens while they are signed in. */
export function useConversation(enabled: boolean) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: helpKey,
    queryFn: () => currentConversation(token),
    enabled: enabled && token !== undefined,
    // Only this tab changes it, and each change is written here.
    staleTime: Infinity,
  })
}

/**
 * Sends the Customer's message and adds it, with the reply, to the transcript. A Conversation that
 * has lapsed since it loaded (a 404) is replaced by a new one, which the message goes to instead.
 */
export function useSendMessage() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (text: string) => {
      const send = (id: string) =>
        api<HelpMessage>('support-assistant', `/conversations/${encodeURIComponent(id)}/messages`, {
          method: 'POST',
          body: { text },
          token,
        })
      const conversation = queryClient.getQueryData<Conversation>(helpKey) ?? (await currentConversation(token))
      try {
        return { conversation, reply: await send(conversation.id) }
      } catch (error) {
        if (!isNotFound(error)) throw error
        const fresh = await startConversation(token)
        return { conversation: fresh, reply: await send(fresh.id) }
      }
    },
    onSuccess: ({ conversation, reply }, text) => {
      const asked: HelpMessage = { role: 'CUSTOMER', text, references: [], at: reply.at }
      queryClient.setQueryData<Conversation>(helpKey, {
        id: conversation.id,
        messages: [...conversation.messages, asked, reply],
      })
    },
  })
}

/** Ends the Conversation, if there is one, and starts a new one. */
export function useNewConversation() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      await endConversation(token)
      return startConversation(token)
    },
    onSuccess: (conversation) => queryClient.setQueryData(helpKey, conversation),
  })
}

/**
 * Ends the kept Conversation and forgets it, as signing out does, so the next person at the computer
 * can't read it. One that already ended or lapsed, or can't be reached, is forgotten all the same.
 */
export async function endConversation(token: string | undefined): Promise<void> {
  const id = storedConversationId()
  storeConversationId(undefined)
  if (!id) return
  await api('support-assistant', `/conversations/${encodeURIComponent(id)}`, { method: 'DELETE', token }).catch(
    () => undefined,
  )
}
