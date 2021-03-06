package org.daiv.websocket

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import mu.KotlinLogging
import org.daiv.util.Registerer

fun <T : Any> EBMessageHeader.toObject(context: SerializersModule, serializer: KSerializer<T>): T {
    return parse(context, serializer).e
}

class TranslaterBuilder(val context: SerializersModule, val list: List<Translater<*>> = listOf()) {
    fun <T : Any> next(serializer: KSerializer<T>, fct: suspend (T) -> Unit): TranslaterBuilder =
        TranslaterBuilder(context, list + Translater(serializer, context, fct))

    fun <T : Any> next(serializer: KSerializer<T>, fct: suspend (T, EBDataHandler) -> Unit): TranslaterBuilder =
        TranslaterBuilder(context, list + Translater(serializer, context, fct))

    fun <T : Any> next(
        serializer: KSerializer<T>,
        fct: suspend (T, FrontendMessageHeader, EBDataHandler) -> Unit
    ): TranslaterBuilder = TranslaterBuilder(context, list + Translater(serializer, context, fct))
}

class Translater<T : Any> internal constructor(
    val serializer: KSerializer<T>,
    val context: SerializersModule,
    val fct: suspend (T, FrontendMessageHeader, EBDataHandler) -> Unit
) {
    companion object {
        private val logger = KotlinLogging.logger("org.daiv.websocket.Translater")
    }

    constructor(serializer: KSerializer<T>, context: SerializersModule, fct: suspend (T) -> Unit) : this(
        serializer,
        context,
        { it, _, _ -> fct(it) })

//    constructor(
//        serializer: KSerializer<T>, context: SerialModule, fct: suspend (T, FrontendMessageHeader) -> Unit
//    ) : this(serializer, context, { it, f, _ -> fct(it, f) })

    constructor(serializer: KSerializer<T>, context: SerializersModule, fct: suspend (T, EBDataHandler) -> Unit) : this(
        serializer,
        context,
        { it, _, e -> fct(it, e) })

    val name
        get() = serializer.descriptor.serialName

    suspend fun call(
        ebWebsocket: EBDataHandler,
        messageHeader: EBMessageHeader,
        func: (FrontendMessageHeader) -> Unit
    ): Boolean {
        if (messageHeader.body == name) {
            logger.trace { "hit on $name" }
            val message = messageHeader.parse(context, serializer)
            logger.trace { "message was parsed for $name - $message" }
            func(message.messageHeader)
            logger.trace { "call function ${message.e}" }
            try {
                fct(message.e, message.messageHeader, ebWebsocket)
            } catch (t:Throwable){
                logger.error(t) { "calling fct of $name failed with exception $t"  }
            }
            logger.trace { "function was called for $name" }
            return true
        }
        return false
    }
}

fun <T : Any> tranlaterWithEB(
    serializer: KSerializer<T>,
    context: SerializersModule = EmptySerializersModule,
    fct: suspend (T, EBDataHandler) -> Unit
) = Translater(serializer, context, fct)

fun <T : Any> translater(
    serializer: KSerializer<T>,
    context: SerializersModule = EmptySerializersModule,
    fct: suspend (T) -> Unit
) =
    Translater(serializer, context, fct)

interface DataSender : PlainDataSender, Registerer<DataReceiver> {
}

fun interface PlainDataSender {
    fun send(message: String)
}

interface DataReceiver {
    suspend fun receive(string: String)
}

class EBDataHandler(
    val dataSender: DataSender,
    val context: SerializersModule = EmptySerializersModule,
    private val translaters: List<Translater<out WSEvent>> = emptyList(),
    private val onHeader: (FrontendMessageHeader) -> Unit = {}
) : DataReceiver {
    companion object {
        private val logger = KotlinLogging.logger("org.daiv.websocket.DataSender")
    }

    init {
        dataSender.register(this)
    }

    var currentTranslaters: () -> List<Translater<out WSEvent>> = { emptyList() }

    private val responseTranslaters = mutableListOf<Translater<out WSEvent>>()
    fun <T : Any> send(serializer: KSerializer<T>, t: T) {
        send(toJSON(serializer, t, context = context))
    }

    fun <T : Any, R : Any> send(serializer: KSerializer<T>, t: T, response: KSerializer<R>, func: suspend (R) -> Unit) {
        send(
            toJSON(serializer, t, context = context),
            Translater(response, context, func) as Translater<out WSEvent>
        )
    }

    fun <T : Any> send(messageHeader: EBMessageHeader, serializer: KSerializer<T>, func: suspend (T) -> Unit) {
        send(messageHeader, Translater(serializer, context, func) as Translater<out WSEvent>)
    }

    fun send(messageHeader: EBMessageHeader, translater: Translater<out WSEvent>? = null) {
        logger.trace { "send messageHeader: $messageHeader" }
        translater?.let { responseTranslaters.add(translater) }
        dataSender.send(messageHeader.serialize())
    }

    private fun isResponse(messageHeader: EBMessageHeader) = responseTranslaters.any { it.name == messageHeader.body }

    private suspend fun run(messageHeader: EBMessageHeader): Boolean {
        try {
            return if (isResponse(messageHeader)) {
                val response = responseTranslaters.find { it.call(this, messageHeader, onHeader) }!!
                responseTranslaters.remove(response)
                true
            } else {
                val complete = translaters + currentTranslaters()
                complete.takeWhile { !it.call(this, messageHeader, onHeader) }.size != complete.size
            }
        } catch (t: Throwable) {
            logger.error(t) { "error while trying to read message from backend" }
            return false
        }
    }

    override suspend fun receive(string: String) {
        logger.debug { "received $string" }
        val parse1 = EBMessageHeader.parse(string)
        logger.trace { "message: $parse1" }
        val exec = run(parse1)
        if (!exec) {
            logger.warn { "received unhandled message: $parse1" }
        }
    }
}

