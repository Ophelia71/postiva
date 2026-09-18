import { Client } from '@stomp/stompjs'
import { useEffect, useRef } from 'react'

const defaultBrokerProtocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
const defaultBrokerUrl = `${defaultBrokerProtocol}://${window.location.host}/ws-social`
const facebookTopic = '/topic/facebook'
const commentsTopic = '/topic/comments'
const refetchEventTypes = new Set([
  'FACEBOOK_POST_CREATED',
  'FACEBOOK_POST_SYNCED',
  'FACEBOOK_POST_INTERACTIONS_SYNCED',
  'FACEBOOK_POST_SCHEDULED',
  'FACEBOOK_POST_UPDATED',
  'FACEBOOK_POST_DELETED',
  'FACEBOOK_POST_FAILED',
])

export type FacebookRealtimeEvent = {
  type?: string
  pageId?: string
  postId?: string
  createdPost?: boolean
}

export function createSocialRealtimeClient() {
  return new Client({
    brokerURL: import.meta.env.VITE_WS_URL ?? defaultBrokerUrl,
    reconnectDelay: 5000,
  })
}

export function useFacebookRealtimeRefetch(onRefetch: (event: FacebookRealtimeEvent) => void) {
  const onRefetchRef = useRef(onRefetch)

  useEffect(() => {
    onRefetchRef.current = onRefetch
  }, [onRefetch])

  useEffect(() => {
    const client = createSocialRealtimeClient()
    client.onConnect = () => {
      client.subscribe(facebookTopic, (message) => {
        const event = parseFacebookRealtimeEvent(message.body)
        if (event.type && refetchEventTypes.has(event.type)) {
          onRefetchRef.current(event)
        }
      })
    }
    client.activate()

    return () => {
      void client.deactivate()
    }
  }, [])
}

export function useFacebookCommentsRealtimeRefetch(
  postId: string,
  onRefetch: (event: FacebookRealtimeEvent) => void,
) {
  const onRefetchRef = useRef(onRefetch)

  useEffect(() => {
    onRefetchRef.current = onRefetch
  }, [onRefetch])

  useEffect(() => {
    if (!postId) {
      return undefined
    }

    const client = createSocialRealtimeClient()
    client.onConnect = () => {
      client.subscribe(commentsTopic, (message) => {
        const event = parseFacebookRealtimeEvent(message.body)
        if (event.postId === postId) {
          onRefetchRef.current(event)
        }
      })
    }
    client.activate()

    return () => {
      void client.deactivate()
    }
  }, [postId])
}

function parseFacebookRealtimeEvent(payload: string): FacebookRealtimeEvent {
  try {
    const value = JSON.parse(payload) as FacebookRealtimeEvent
    return value && typeof value === 'object' ? value : {}
  } catch {
    return {}
  }
}
