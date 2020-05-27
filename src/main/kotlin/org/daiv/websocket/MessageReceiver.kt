package org.daiv.websocket

interface MessageReceiver<T : WSEvent> {
    suspend fun onMessage(event: T)
}
