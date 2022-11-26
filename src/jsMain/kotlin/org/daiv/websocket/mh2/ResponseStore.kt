package org.daiv.websocket.mh2

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.daiv.websocket.startWebsocket
import org.w3c.dom.WebSocket
import kotlin.js.Date

actual fun timeId() = Date().toISOString()


class JSSendable(val ws: WebSocket = startWebsocket()) : WSSendable {
    companion object {
        private val logger = KotlinLogging.logger("org.daiv.websocket.mh2.JSSendable")
    }

    override suspend fun send(messageHeader: SendSerializable) {
        ws.send(messageHeader.serialize())
    }
    override suspend fun receive(messageHandler: MessageHandler){
        ws.onopen = {
            messageHandler.onOpen(it.toString())
        }
        ws.onmessage = {
            messageHandler.onText(it.data.toString())
        }
        ws.onclose ={
            messageHandler.onClose(it.toString())
        }
        ws.onerror ={
            messageHandler.onError(it.toString())
        }
    }

    override fun isActive() = ws.readyState == WebSocket.OPEN
}
typealias DMHJSWebsocket = JSWebsocket<DoubleMessageHeader, DMHSerializableKey>

class JSWebsocket<MESSAGE, MSGBUILDERKEY : Any>(
    val websocketBuilder: WebsocketBuilder<MESSAGE, JSWebsocket<MESSAGE, MSGBUILDERKEY>, MSGBUILDERKEY>,
    val handler:suspend (JSWebsocket<MESSAGE, MSGBUILDERKEY>) -> Unit
) : WebsocketInterface<MESSAGE, JSWebsocket<MESSAGE, MSGBUILDERKEY>> by websocketBuilder
where MESSAGE :MessageIdable,
      MESSAGE:SendSerializable{
    companion object {
        private val logger = KotlinLogging.logger("org.daiv.websocket.mh2.DMHJSWebsocket")
    }

    init {
        websocketBuilder.scopeContextable.launch("receive") {
            websocketBuilder.sendable.receive(object : MessageHandler {
                override fun onOpen(openInfo: String?) {
                    logger.info { "websocket opened: $openInfo" }
                    GlobalScope.launch {
                        handler(this@JSWebsocket)
                    }
                }

                override fun onClose(closeInfo: String?) {
                    logger.info { "websocket closed $closeInfo" }
                }

                override fun onError(errorInfo: String?) {
                    websocketBuilder.errorLogger.onError("websocket error: $errorInfo", null)
                }

                override fun onText(text: String) {
                    websocketBuilder.scopeContextable.launch("onText $text coroutine") {
                        val p = try {
                            websocketBuilder.messageFactory.parse(text)
                        } catch (t: Throwable) {
                            logger.error(t) { "message: $text could not be parsed" }
                            return@launch
                        }
                        try {
                            onMessage(this@JSWebsocket, p)
                        } catch (t: Throwable) {
                            logger.error(t) { "message handling of $p failed" }
                        }
                    }
                }
            })
        }
    }
}
