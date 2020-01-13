package org.daiv.websocket

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.w3c.dom.CloseEvent
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event
import kotlin.browser.window


private val logger = KotlinLogging.logger("org.daiv.websocket.eventbus")


fun <T : Any> KSerializer<T>.toName() = descriptor.name

fun <T : Any> EBMessageHeader.toMessage(serializer: KSerializer<T>): Message<FrontendMessageHeader, T> {
    return Json.nonstrict.parse(Message.serializer(FrontendMessageHeader.serializer(), serializer), json)
}

fun <T : Any> EBMessageHeader.toObject(serializer: KSerializer<T>): T {
    return toMessage(serializer).e
}

class Translater<T : Any> internal constructor(
    val serializer: KSerializer<T>,
    val fct: suspend (T, FrontendMessageHeader, EBWebsocket) -> Unit
) {
    constructor(serializer: KSerializer<T>, fct: suspend (T) -> Unit) : this(serializer, { it, _, _ -> fct(it) })
    constructor(serializer: KSerializer<T>, fct: suspend (T, FrontendMessageHeader) -> Unit) : this(
        serializer,
        { it, f, _ -> fct(it, f) })

    constructor(serializer: KSerializer<T>, fct: suspend (T, EBWebsocket) -> Unit) : this(
        serializer,
        { it, _, e -> fct(it, e) })

    val name
        get() = serializer.descriptor.name

    suspend fun call(
        ebWebsocket: EBWebsocket,
        messageHeader: EBMessageHeader,
        func: (FrontendMessageHeader) -> Unit
    ): Boolean {
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

fun <T : Any> tranlaterWithEB(serializer: KSerializer<T>, fct: suspend (T, EBWebsocket) -> Unit) =
    Translater(serializer, fct)

fun <T : Any> translater(serializer: KSerializer<T>, fct: suspend (T) -> Unit) = Translater(serializer, fct)

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

interface DataSender {
    fun send(messageHeader: EBMessageHeader, translater: Translater<out WSEvent>? = null)

    fun <T : Any> send(serializer: KSerializer<T>, t: T) {
        send(toJSON(serializer, t))
    }

    fun <T : Any, R : Any> send(serializer: KSerializer<T>, t: T, response: KSerializer<R>, func: suspend (R) -> Unit) {
        send(toJSON(serializer, t), Translater(response, func) as Translater<out WSEvent>)
    }

    fun <T : Any> send(messageHeader: EBMessageHeader, serializer: KSerializer<T>, func: suspend (T) -> Unit) {
        send(messageHeader, Translater(serializer, func) as Translater<out WSEvent>)
    }
}

interface WebSocketSender : DataSender {
    var currentTranslaters: () -> List<Translater<out WSEvent>>
    var onclose: (Event) -> Unit
    var onopen: (Event) -> Unit
    var onerror: (Event) -> Unit
}

class EBWebsocket(
    private val translaters: List<Translater<out WSEvent>> = emptyList(),
    private val ws: WebSocket = startWebsocket(),
    private val onHeader: (FrontendMessageHeader) -> Unit = {}
) : WebSocketSender {
    companion object {
        private val logger = KotlinLogging.logger("org.daiv.websocket.eventbus")
    }

    override var currentTranslaters: () -> List<Translater<out WSEvent>> = { emptyList() }
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

    private val responseTranslaters = mutableListOf<Translater<out WSEvent>>()

    override fun send(messageHeader: EBMessageHeader, translater: Translater<out WSEvent>?) {
        logger.trace { "send messageHeader: $messageHeader" }
        translater?.let { responseTranslaters.add(translater) }
        ws.send(messageHeader.serialize())
    }

    private fun isResponse(messageHeader: EBMessageHeader) = responseTranslaters.any { it.name == messageHeader.body }

    private suspend fun run(messageHeader: EBMessageHeader): Boolean {
        return if (isResponse(messageHeader)) {
            val response = responseTranslaters.find { it.call(this, messageHeader, onHeader) }!!
            responseTranslaters.remove(response)
            true
        } else {
            val complete = translaters + currentTranslaters()
            complete.takeWhile { !it.call(this, messageHeader, onHeader) }.size != complete.size
        }
    }

    private suspend fun parse(event: MessageEvent) {
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

