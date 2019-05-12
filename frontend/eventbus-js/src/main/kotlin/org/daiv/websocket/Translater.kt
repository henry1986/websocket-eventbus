package org.daiv.websocket

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JSON
import mu.KotlinLogging
import org.w3c.dom.CloseEvent
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event
import kotlin.browser.window

fun <HEADER : Any, BODY : Any> toMessage(
    serializer: KSerializer<HEADER>, bodySerializer: KSerializer<BODY>,
    header: HEADER, body: BODY
): String {
    val s = Message.serializer(serializer, bodySerializer)
    val e = Message(header, body)
    return JSON.nonstrict.stringify(s, e)
}

val logger = KotlinLogging.logger {}
inline fun <reified T : Any> toJSON(serializer: KSerializer<T>, event: T): EBMessageHeader {
    logger.trace { event }
    return EBMessageHeader(
        FrontendMessageHeader.serializer().descriptor.name, serializer.descriptor.name,
        toMessage(
            FrontendMessageHeader.serializer(), serializer, FrontendMessageHeader("", false), event
        )
    )
}

fun <T : Any> toName(serializer: KSerializer<T>) = serializer.descriptor.name


fun <T : Any> EBMessageHeader.toMessage(serializer: KSerializer<T>): Message<FrontendMessageHeader, T> {
    return JSON.parse(Message.serializer(FrontendMessageHeader.serializer(), serializer), json)
}

fun <T : Any> EBMessageHeader.toObject(serializer: KSerializer<T>): T {
    return toMessage(serializer).e
}

class Translater<T : Any>(val serializer: KSerializer<T>, val fct: (T, EBWebsocket) -> Unit) {
    constructor(serializer: KSerializer<T>, fct: (T) -> Unit) : this(serializer, { it, eb -> fct(it) })

    fun call(ebWebsocket: EBWebsocket, messageHeader: EBMessageHeader, func: (FrontendMessageHeader) -> Unit): Boolean {
        val name = toName(serializer)
        if (messageHeader.body == name) {
            logger.trace { "hit on $name" }
            val message = messageHeader.toMessage(serializer)
            func(message.messageHeader)
            fct(message.e, ebWebsocket)
            return true
        }
        return false
    }
}

fun startWebsocket(onHostEmptyUrl: String = "127.0.0.1:8080"): WebSocket {
    logger.trace { "protocol: ${window.location.protocol}" }
    val protocol = if (window.location.protocol == "http:" || window.location.protocol == "file:") "ws" else "wss"
    logger.trace { "ws protocol: $protocol" }
    logger.trace { "window location host: ${window.location.host}" }
    val host = if (window.location.host == "") onHostEmptyUrl else window.location.host

    logger.trace { "host: $host" }
    val uri = "$protocol://$host/ws"
    logger.trace { "uri: $uri" }
    val ws = WebSocket(uri)

    logger.trace { "ws: $ws" }
    return ws
}

class EBWebsocket(
    private val translaters: List<Translater<out WSEvent>> = emptyList(),
    private val ws: WebSocket = startWebsocket(),
    private val onHeader: (FrontendMessageHeader) -> Unit = {}
) {
    companion object {
        val logger = KotlinLogging.logger { }
    }

    var currentTranslaters: () -> List<Translater<out WSEvent>> = { emptyList() }
    var onclose: (Event) -> Unit = {}
    var onopen: (Event) -> Unit = {}
    var onerror: (Event) -> Unit = {}

    init {
        ws.onmessage = { parse(it) }
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

    fun send(messageHeader: EBMessageHeader) {
        ws.send(messageHeader.serialize())
    }

    private fun run(messageHeader: EBMessageHeader): Boolean {
        val complete = translaters + currentTranslaters()
        return complete.takeWhile { !it.call(this, messageHeader, onHeader) }.size != complete.size
    }

    private fun parse(event: MessageEvent) {
        val string = event.data.toString()
        logger.trace { "received $string" }
        val parse1 = EBMessageHeader.parse(string)
        logger.trace { "message: $parse1" }
        val exec = run(parse1)
        if (!exec) {
            logger.trace { "received unhandled message: $parse1" }
        }
    }
}
