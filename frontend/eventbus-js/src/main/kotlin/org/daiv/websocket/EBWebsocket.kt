package org.daiv.websocket

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.EmptyModule
import kotlinx.serialization.modules.SerialModule
import mu.KotlinLogging
import org.daiv.util.DefaultRegisterer
import org.daiv.util.Registerer
import org.w3c.dom.CloseEvent
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event
import kotlin.browser.window


private val logger = KotlinLogging.logger("org.daiv.websocket.eventbus")


fun startWebsocket(onHostEmptyUrl: String = "127.0.0.1:8080"): WebSocket {
    logger.debug { "protocol: ${window.location.protocol}" }
    val protocol = if (window.location.protocol == "http:" || window.location.protocol == "file:") "ws" else "wss"
    logger.debug { "ws protocol: $protocol" }
    logger.debug { "window location host: ${window.location.host}" }
    val host = if (window.location.host == "") onHostEmptyUrl else window.location.host

    logger.debug { "host: $host" }
    val uri = "$protocol://$host/ws"
    logger.debug { "uri: $uri" }
    val ws = WebSocket(uri)

    logger.trace { "ws: $ws" }
    return ws
}

interface WebSocketSender : DataSender {
    var onclose: (Event) -> Unit
    var onopen: (Event) -> Unit
    var onerror: (Event) -> Unit
}

class EBWebsocket(
    private val ws: WebSocket = startWebsocket(),
    private val registerer: DefaultRegisterer<DataReceiver> = DefaultRegisterer()
) : DataSender, WebSocketSender, Registerer<DataReceiver> by registerer{
    companion object {
        private val logger = KotlinLogging.logger("org.daiv.websocket.eventbus")
    }


    override var onclose: (Event) -> Unit = {}
    override var onopen: (Event) -> Unit = {}
    override var onerror: (Event) -> Unit = {}

    init {
        ws.onmessage = { GlobalScope.launch { parse(it) } }
        ws.onclose = {
            it as CloseEvent
            logger.info { "ws: $ws was closed -> reason: ${it.reason}, code: ${it.code}" }
            this@EBWebsocket.onclose(it)
        }
        ws.onopen = {
            logger.info { "ws: $ws was opened" }
            this@EBWebsocket.onopen(it)
        }
        ws.onerror = {
            logger.error { "ws: $ws has an error: $it" }
            this@EBWebsocket.onerror(it)
        }
    }

    override fun send(message: String) {
        ws.send(message)
    }

    private suspend fun parse(event: MessageEvent) {
        val string = event.data.toString()
        registerer.suspendEvent { receive(string) }
    }
}

