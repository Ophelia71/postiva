import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useEffect, useMemo, useState } from 'react'

export type RealtimeLogEntry = {
  id: string
  topic: string
  payload: string
  receivedAt: string
}

const defaultWebSocketUrl = 'ws://localhost:8080/ws-social'
const defaultSockJsUrl = 'http://localhost:8080/ws-social'
const defaultTopics = ['/topic/comments']

export function useWebSocket(topics: string[] = defaultTopics) {
  const [logs, setLogs] = useState<RealtimeLogEntry[]>([])
  const [connected, setConnected] = useState(false)

  const stableTopics = useMemo(() => topics, [topics])

  useEffect(() => {
    const useSockJs = import.meta.env.VITE_WS_TRANSPORT === 'sockjs'
    const client = new Client({
      brokerURL: useSockJs
        ? undefined
        : (import.meta.env.VITE_WS_URL ?? defaultWebSocketUrl),
      webSocketFactory: useSockJs
        ? () =>
            new SockJS(import.meta.env.VITE_SOCKJS_URL ?? defaultSockJsUrl) as WebSocket
        : undefined,
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true)
        stableTopics.forEach((topic) => {
          client.subscribe(topic, (message: IMessage) => {
            appendLog(topic, message.body)
          })
        })
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => {
        appendLog('stomp:error', frame.body || frame.headers.message || 'STOMP error')
      },
      onWebSocketClose: () => setConnected(false),
    })

    client.activate()

    return () => {
      void client.deactivate()
    }
  }, [stableTopics])

  function appendLog(topic: string, payload: string) {
    setLogs((currentLogs) => [
      {
        id: crypto.randomUUID(),
        topic,
        payload: formatPayload(payload),
        receivedAt: new Date().toLocaleTimeString(),
      },
      ...currentLogs,
    ])
  }

  return { connected, logs }
}

function formatPayload(payload: string) {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}
