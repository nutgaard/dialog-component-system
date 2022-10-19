package no.nutgaard.websocket

import io.ktor.websocket.*

class WsServer {
    private val sessions = HashSet<WebSocketSession>()
    suspend fun send(message: String) {
        for (session in sessions) {
            session.send(Frame.Text(message))
        }
        println("[WS] $message")
    }

    fun register(session: WebSocketSession) {
        sessions.add(session)
    }

    fun unregister(session: WebSocketSession) {
        sessions.remove(session)
    }
}
