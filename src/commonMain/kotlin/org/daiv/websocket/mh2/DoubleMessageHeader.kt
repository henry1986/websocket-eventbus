package org.daiv.websocket.mh2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import mu.KotlinLogging
import org.daiv.websocket.Message
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun <T> KSerializer<T>.serialName() = descriptor.serialName

data class DMHSerializableKey(
    val headerSerialName: String,
    val bodySerialName: String,
    val message: String
)

object DMHSendSerializable : SendSerializableBuilder<DMHSerializableKey> {
    override fun toMessageHeader(
        msgbuilderkey: DMHSerializableKey,
        isResponse: Boolean,
        responseId: String
    ): SendSerializable {
        return DoubleMessageHeader(
            msgbuilderkey.headerSerialName,
            msgbuilderkey.bodySerialName,
            null,
            isResponse,
            responseId,
            msgbuilderkey.message
        )
    }

    override fun error(
        errorMessage: String,
        msgbuilderkey: DMHSerializableKey,
        isResponse: Boolean,
        responseId: String
    ): SendSerializable {
        return DoubleMessageHeader(
            msgbuilderkey.headerSerialName,
            msgbuilderkey.bodySerialName,
            errorMessage,
            isResponse,
            responseId,
            msgbuilderkey.message
        )
    }
}


fun <HEADER : Any, T : Any> DoubleMessageHeader.check(
    headerSerializer: KSerializer<HEADER>,
    serializer: KSerializer<T>
) = this.headerSerializer == headerSerializer.serialName() && this.bodySerializer == serializer.serialName()

interface DMHMessageChecker<HEADER : Any, T : Any> : MessageChecker<DoubleMessageHeader> {
    val headerSerializer: KSerializer<HEADER>
    val serializer: KSerializer<T>
    override fun isMessage(messageData: DoubleMessageHeader) = messageData.check(headerSerializer, serializer)
    fun DoubleMessageHeader.getMessage(context: SerializersModule): Message<HEADER, T> {
        return Message.serializer(this@DMHMessageChecker.headerSerializer, serializer).parse(context, this.message)
    }
}

data class DMHRequestResponse<HEADER : Any, T : Any, R : Any>(
    override val headerSerializer: KSerializer<HEADER>,
    override val serializer: KSerializer<T>,
    val responseSerializer: KSerializer<R>,
    val response: suspend (HEADER, T) -> Message<HEADER, R>
) : RequestResponse<DoubleMessageHeader, DMHSerializableKey>, DMHMessageChecker<HEADER, T> {
    override suspend fun answer(context: SerializersModule, message: DoubleMessageHeader): DMHSerializableKey {
        val deserializedMessage = message.getMessage(context)
        val mReturn = response(deserializedMessage.messageHeader, deserializedMessage.e)
        val message = Message.serializer(headerSerializer, responseSerializer).stringify(mReturn, context)
        return DMHSerializableKey(headerSerializer.serialName(), responseSerializer.serialName(), message)
    }

    override val errorBuilderKey: DMHSerializableKey =
        DMHSerializableKey(headerSerializer.serialName(), responseSerializer.serialName(), "")
}

class DMHAnswerOnRequest(
    override val list: List<RequestResponse<DoubleMessageHeader, DMHSerializableKey>>,
    sendable: WSSendable,
    errorLogger: WSErrorLogger
) : AnswerOnRequest<DoubleMessageHeader, DMHSerializableKey>, WSSendable by sendable, WSErrorLogger by errorLogger {
    override val sendSerializableBuilder: SendSerializableBuilder<DMHSerializableKey> = DMHSendSerializable
}

data class DMHRequestHandler<HEADER : Any, T : Any>(
    override val headerSerializer: KSerializer<HEADER>,
    override val serializer: KSerializer<T>,
    val block: suspend (HEADER, T) -> Unit
) : WSRequestHandler<DoubleMessageHeader>, DMHMessageChecker<HEADER, T> {

    override suspend fun answer(messageData: DoubleMessageHeader, context: SerializersModule) {
        val deserializedMessage = messageData.getMessage(context)
        block(deserializedMessage.messageHeader, deserializedMessage.e)
    }
}

data class DMHRequestHolder(override val wsResponses: List<WSRequestHandler<DoubleMessageHeader>>) :
    WSResponseAble<DoubleMessageHeader>

interface MessageReceiver2<MESSAGE : MessageIdable> : SerializersModuleable {
    companion object {
        private val logger = KotlinLogging.logger("org.daiv.websocket.mh2.MessageReceiver2")
    }

    val handlers: List<RequestHolderHandler<MESSAGE>>

    suspend fun onMessage(ebMessageHeader: MESSAGE) {
        handlers.find { it.handle(ebMessageHeader) }?.let {
            try {
                logger.trace { "handle: $it $ebMessageHeader" }
                it.doHandle(ebMessageHeader, context)
                logger.trace { "was handled: $ebMessageHeader" }
            } catch (t: Throwable) {
                logger.error(t) { "catched when handled by $it" }
            }
        } ?: run {
            logger.error { "no handler could handle: $ebMessageHeader" }
        }
    }
}

interface SerializersModuleable {
    val context: SerializersModule
}

class DMHResponseStorable(override val responseStore: IdGetter<DoubleMessageHeader>) :
    ResponseStorable<DoubleMessageHeader>

interface DMHSender : SerializersModuleable {
    val responseStore: ResponseStore<DoubleMessageHeader>
    val sendable: WSSendable


    suspend fun <HEADER : Any, T : Any, R : Any> send(
        headerSerializer: KSerializer<HEADER>,
        serializer: KSerializer<T>,
        header: HEADER,
        t: T,
        responseSerializer: KSerializer<R>,
        response: suspend (HEADER, R) -> Unit
    ) {
        responseStore.storeTranslator({
            val m = Message.serializer(headerSerializer, responseSerializer).parse(context, it.message)
            response(m.messageHeader, m.e)
        }) {
            send(headerSerializer, serializer, header, t, it)
        }
    }

    private suspend fun <HEADER : Any, T : Any> send(
        headerSerializer: KSerializer<HEADER>,
        serializer: KSerializer<T>,
        header: HEADER,
        t: T,
        responseId: String?
    ) {
        val m = Message(header, t)
        val messageSerializer = Message.serializer(headerSerializer, serializer)
        val messageAsString = messageSerializer.stringify(m, context)
        val header = DoubleMessageHeader(
            headerSerializer.serialName(),
            serializer.serialName(),
            null,
            false,
            responseId,
            messageAsString
        )
        sendable.send(header)
    }

    suspend fun <HEADER : Any, T : Any> send(
        headerSerializer: KSerializer<HEADER>,
        serializer: KSerializer<T>,
        header: HEADER,
        t: T,
    ) {
        send(headerSerializer, serializer, header, t, null)
    }
}


interface DMHWebsocketInterface : MessageReceiver2<DoubleMessageHeader>, DMHSender

class DMHWebsocketBuilder<T : WSSendable>(
    override val sendable: T,
    requestResponses: List<RequestResponse<DoubleMessageHeader, DMHSerializableKey>> = emptyList(),
    requestHandler: List<WSRequestHandler<DoubleMessageHeader>> = emptyList(),
    val errorLogger: WSErrorLogger = WSErrorLogger { },
    override val context: SerializersModule = EmptySerializersModule,
    val scope: CoroutineScope = GlobalScope,
    val coroutineContext: CoroutineContext = EmptyCoroutineContext
) : DMHWebsocketInterface {
    override val responseStore: ResponseStore<DoubleMessageHeader> = ResponseStore(scope, coroutineContext)
    override val handlers: List<RequestHolderHandler<DoubleMessageHeader>> = listOf(
        DMHResponseStorable(responseStore),
        DMHAnswerOnRequest(requestResponses, sendable, errorLogger),
        DMHRequestHolder(requestHandler)
    )
}

data class DoubleMessageHeader(
    val header: String,
    val headerSerializer: String,
    val bodySerializer: String,
    val errorMessage: String?,
    override val isResponse: Boolean,
    override val responseId: String?,
    val message: String
) : SendSerializable, MessageIdable {

    constructor(
        headerSerializer: String,
        bodySerializer: String,
        errorMessage: String?,
        isResponse: Boolean,
        responseId: String?,
        message: String
    ) : this("DMH", headerSerializer, bodySerializer, errorMessage, isResponse, responseId, message)

    override fun serialize() =
        "[\"$header\", \"$headerSerializer\", \"$bodySerializer\",\"$errorMessage\", $isResponse, \"$responseId\", json = $message]"

    companion object {
        private fun String.toNull() = if (this == "null") null else this

        fun parse(string: String): DoubleMessageHeader {
            val list = HeaderParser.headerParsed(string).parseNextString().removeComma().parseNextString().removeComma()
                .parseNextString().removeComma().parseNextString().removeComma().parseNoString().removeComma()
                .parseNextString().removeComma()
                .takeAll().list
            return DoubleMessageHeader(
                list.first(),
                list[1],
                list[2],
                list[3].toNull(),
                list[4].toBoolean(),
                list[5].toNull(),
                list[6]
            )
        }
    }
}
