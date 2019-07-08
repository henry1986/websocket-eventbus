package org.daiv.websocket

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JSON
import mu.KotlinLogging
import org.w3c.dom.CloseEvent
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event
import kotlin.browser.window
import kotlin.js.Date

fun <HEADER : Any, BODY : Any> toMessage(
    serializer: KSerializer<HEADER>, bodySerializer: KSerializer<BODY>,
    header: HEADER, body: BODY
): String {
    val s = Message.serializer(serializer, bodySerializer)
    val e = Message(header, body)
    return JSON.nonstrict.stringify(s, e)
}

private val logger = KotlinLogging.logger("org.daiv.websocket.eventbus")

inline fun <reified T : Any> toJSON(
    serializer: KSerializer<T>,
    event: T,
    req: FrontendMessageHeader? = null
): EBMessageHeader {
    val resString = req?.messageId ?: "${serializer.descriptor.name}-${Date().toISOString()}"
    return EBMessageHeader(
        FrontendMessageHeader.serializer().descriptor.name, serializer.descriptor.name,
        toMessage(
            FrontendMessageHeader.serializer(),
            serializer,
            FrontendMessageHeader("", false, resString),
            event
        )
    )
}

fun <T : Any> KSerializer<T>.toName() = descriptor.name

fun <T : Any> EBMessageHeader.toMessage(serializer: KSerializer<T>): Message<FrontendMessageHeader, T> {
    return JSON.parse(Message.serializer(FrontendMessageHeader.serializer(), serializer), json)
}

fun <T : Any> EBMessageHeader.toObject(serializer: KSerializer<T>): T {
    return toMessage(serializer).e
}

class Translater<T : Any> internal constructor(
    val serializer: KSerializer<T>,
    val fct: (T, FrontendMessageHeader, EBWebsocket) -> Unit
) {
    constructor(serializer: KSerializer<T>, fct: (T) -> Unit) : this(serializer, { it, _, _ -> fct(it) })
    constructor(serializer: KSerializer<T>, fct: (T, FrontendMessageHeader) -> Unit) : this(
        serializer,
        { it, f, _ -> fct(it, f) })

    constructor(serializer: KSerializer<T>, fct: (T, EBWebsocket) -> Unit) : this(
        serializer,
        { it, _, e -> fct(it, e) })

    val name
        get() = serializer.descriptor.name

    fun call(ebWebsocket: EBWebsocket, messageHeader: EBMessageHeader, func: (FrontendMessageHeader) -> Unit): Boolean {
        if (messageHeader.body == name) {
            logger.trace { "hit on $name" }
            val message = messageHeader.toMessage(serializer)
            func(message.messageHeader)
            fct(message.e, message.messageHeader, ebWebsocket)
            return true
        }
        return false
    }
}

fun <T : Any> tranlaterWithEB(serializer: KSerializer<T>, fct: (T, EBWebsocket) -> Unit) =
    Translater(serializer, fct)

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

class EBWebsocket(
    private val translaters: List<Translater<out WSEvent>> = emptyList(),
    private val ws: WebSocket = startWebsocket(),
    private val onHeader: (FrontendMessageHeader) -> Unit = {}
) {
    companion object {
        private val logger = KotlinLogging.logger("org.daiv.websocket.eventbus")
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

    private val responseTranslaters = mutableListOf<Translater<out WSEvent>>()

    fun send(messageHeader: EBMessageHeader, translater: Translater<out WSEvent>? = null) {
        logger.trace { "send messageHeader: $messageHeader" }
        translater?.let { responseTranslaters.add(translater) }
        ws.send(messageHeader.serialize())
    }

    fun <T : Any> send(messageHeader: EBMessageHeader, serializer: KSerializer<T>, func: (T) -> Unit) {
        send(messageHeader, Translater(serializer, func) as Translater<out WSEvent>)
    }

    private fun isResponse(messageHeader: EBMessageHeader) = responseTranslaters.any { it.name == messageHeader.body }

    private fun run(messageHeader: EBMessageHeader): Boolean {
        return if (isResponse(messageHeader)) {
            val response = responseTranslaters.find { it.call(this, messageHeader, onHeader) }!!
            responseTranslaters.remove(response)
            true
        } else {
            val complete = translaters + currentTranslaters()
            complete.takeWhile { !it.call(this, messageHeader, onHeader) }.size != complete.size
        }
    }

    private fun parse(event: MessageEvent) {
        val string = event.data.toString()
        logger.debug { "received $string" }
        val parse1 = EBMessageHeader.parse(string)
        logger.trace { "message: $parse1" }
        val exec = run(parse1)
        if (!exec) {
            logger.warn { "received unhandled message: $parse1" }
        }
    }
}
