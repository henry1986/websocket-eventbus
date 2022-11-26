package org.daiv.websocket.mh2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import mu.KotlinLogging
import org.daiv.coroutines.DefaultScopeContextable
import org.daiv.coroutines.ScopeContextable
import org.daiv.websocket.Message
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun <T> KSerializer<T>.serialName() = descriptor.serialName

data class DMHSerializableKey(
    val headerSerialName: String,
    val bodySerialName: String,
    val message: String
)

object DMHMessageFactory : MessageFactory<DoubleMessageHeader, DMHSerializableKey> {
    override fun toMessageHeader(
        msgbuilderkey: DMHSerializableKey,
        isResponse: Boolean,
        responseId: String
    ): DoubleMessageHeader {
        return DoubleMessageHeader(
            msgbuilderkey.headerSerialName,
            msgbuilderkey.bodySerialName,
            null,
            isResponse,
            responseId,
            msgbuilderkey.message
        )
    }

    override fun parse(message: String) = DoubleMessageHeader.parse(message)

    override fun error(
        errorMessage: String,
        msgbuilderkey: DMHSerializableKey,
        isResponse: Boolean,
        responseId: String
    ): DoubleMessageHeader {
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

class DMHAnswerOnRequest<WS:Any>(
    override val list: List<RequestResponse<DoubleMessageHeader, DMHSerializableKey>>,
    sendable: WSSendable,
    errorLogger: WSErrorLogger
) : AnswerOnRequest<DoubleMessageHeader, WS, DMHSerializableKey>, WSSendable by sendable, WSErrorLogger by errorLogger {
    override val messageFactory: MessageFactory<DoubleMessageHeader, DMHSerializableKey> =
        DMHMessageFactory
}

data class DMHRequestHandler<HEADER : Any, T : Any, WS:Any>(
    override val headerSerializer: KSerializer<HEADER>,
    override val serializer: KSerializer<T>,
    val block: suspend (HEADER, T) -> Unit
) : WSRequestHandler<DoubleMessageHeader, WS>, DMHMessageChecker<HEADER, T> {

    override suspend fun answer(ws:WS, messageData: DoubleMessageHeader, context: SerializersModule) {
        val deserializedMessage = messageData.getMessage(context)
        block(deserializedMessage.messageHeader, deserializedMessage.e)
    }
}

data class DMHWebsocketRequestHandler<HEADER : Any, T : Any, WS:Any>(
    override val headerSerializer: KSerializer<HEADER>,
    override val serializer: KSerializer<T>,
    val block: suspend (WS, HEADER, T) -> Unit
) : WSRequestHandler<DoubleMessageHeader, WS>, DMHMessageChecker<HEADER, T> {

    override suspend fun answer(ws:WS, messageData: DoubleMessageHeader, context: SerializersModule) {
        val deserializedMessage = messageData.getMessage(context)
        block(ws, deserializedMessage.messageHeader, deserializedMessage.e)
    }
}

//data class DMHRequestHolder(override val wsResponses: List<WSRequestHandler<DoubleMessageHeader>>) :
//    WSResponseAble<DoubleMessageHeader>

interface MessageReceiver2<MESSAGE : MessageIdable, WS:Any> : SerializersModuleable {
    companion object {
        private val logger = KotlinLogging.logger("org.daiv.websocket.mh2.MessageReceiver2")
    }

    val handlers: List<RequestHolderHandler<MESSAGE, WS>>

    suspend fun onMessage(ws:WS, ebMessageHeader: MESSAGE) {
        handlers.find { it.handle(ebMessageHeader) }?.let {
            try {
                logger.trace { "handle: $it $ebMessageHeader" }
                it.doHandle(ws, ebMessageHeader, context)
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

class DMHSender<WS:Any>(websocketInterface: WebsocketInterface<DoubleMessageHeader, WS>) :
    WebsocketInterface<DoubleMessageHeader, WS> by websocketInterface {

    suspend fun <HEADER : Any, T : Any, R : Any> send(
        headerSerializer: KSerializer<HEADER>,
        serializer: KSerializer<T>,
        header: HEADER,
        t: T,
        responseSerializer: KSerializer<R>,
        response: suspend (HEADER, R) -> Unit
    ) {
        responseStore.storeTranslator({
            val x = Message.serializer(headerSerializer, responseSerializer)
            val m = x.parse(context, it.message)
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

interface Sender<MESSAGE : Any> {
    val responseStore: ResponseStore<MESSAGE>
    val sendable: WSSendable
}


interface MessageFactory<MESSAGE : SendSerializable, MSGBUILDERKEY : Any> {
    fun toMessageHeader(msgbuilderkey: MSGBUILDERKEY, isResponse: Boolean, responseId: String): MESSAGE

    fun error(
        errorMessage: String,
        msgbuilderkey: MSGBUILDERKEY,
        isResponse: Boolean,
        responseId: String
    ): MESSAGE

    fun parse(message: String): MESSAGE
}

interface WebsocketInterface<MESSAGE : MessageIdable, WS:Any> : MessageReceiver2<MESSAGE, WS>,
    Sender<MESSAGE>, ActiveCheck

class WebsocketBuilder<MESSAGE, WS:Any, MSGBUILDERKEY : Any>(
    override val sendable: WSSendable,
    val messageFactory: MessageFactory<MESSAGE, MSGBUILDERKEY>,
    requestResponses: List<RequestResponse<MESSAGE, MSGBUILDERKEY>> = emptyList(),
    requestHandler: List<WSRequestHandler<MESSAGE, WS>> = emptyList(),
    otherHandlers: List<RequestHolderHandler<MESSAGE, WS>> = emptyList(),
    val errorLogger: WSErrorLogger = WSErrorLogger { m, t -> },
    override val context: SerializersModule = EmptySerializersModule,
    val scopeContextable: ScopeContextable = DefaultScopeContextable()
) : WebsocketInterface<MESSAGE, WS>, ActiveCheck by sendable
        where MESSAGE : MessageIdable,
              MESSAGE : SendSerializable {
    override val responseStore: ResponseStore<MESSAGE> = ResponseStore(scopeContextable)
    override val handlers: List<RequestHolderHandler<MESSAGE, WS>> = listOf(
        ResponseStorable(responseStore),
        AWSAnswerable(requestResponses, sendable, errorLogger, messageFactory),
        WSResponseAble(requestHandler)
    ) + otherHandlers
}

//class DMHWebsocketBuilder<T : WSSendable>:WebsocketBuilder<T, DoubleMessageHeader, DMHSendSerializable>()

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
