package org.daiv.websocket.mh2

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.daiv.websocket.startWebsocket
import org.w3c.dom.WebSocket
import kotlin.js.Date

actual fun timeId() = Date().toISOString()


class JSSendable(val ws: WebSocket = startWebsocket()) : WSSendable {
    override suspend fun send(messageHeader: SendSerializable) {
        ws.send(messageHeader.serialize())
    }
}

class DMHJSWebsocket(
    val websocketBuilder: DMHWebsocketBuilder<JSSendable>,
    val handler:suspend (DMHJSWebsocket) -> Unit
) : DMHWebsocketInterface by websocketBuilder {
    companion object {
        private val logger = KotlinLogging.logger("org.daiv.websocket.mh2.DMHJSWebsocket")
    }

    private val ws
        get() = websocketBuilder.sendable.ws

    init {
        ws.onopen = {
            logger.info { "websocket opened: $it" }
            GlobalScope.launch {
                handler(this@DMHJSWebsocket)
            }
        }
        ws.onmessage = {
            websocketBuilder.scope.launch(websocketBuilder.coroutineContext) {
                val p = try {
                    DoubleMessageHeader.parse(it.data.toString())
                } catch (t: Throwable) {
                    logger.error(t) { "message: ${it.data} could not be parsed" }
                    return@launch
                }
                try {
                    onMessage(p)
                } catch (t: Throwable) {
                    logger.error(t) { "message handling of $p failed" }
                }
            }
        }
        ws.onclose = {
            logger.info { "websocket closed $it" }
        }
        ws.onerror = {
            websocketBuilder.errorLogger.onError("websocket error: $it")
        }
    }
}
