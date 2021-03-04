package org.daiv.websocket.mh2

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import mu.KotlinLogging


open class EventbusException(cause: Throwable?, message: String?) : Exception(message, cause) {
    constructor() : this(null, null)
}

interface IdGetter<MESSAGE : Any> {
    suspend fun removeId(responseId: String): ResponseStore.WSResponse<MESSAGE>?
}

interface ResponseStorable<MESSAGE : MessageIdable> : RequestHolderHandler<MESSAGE> {
    class ResponseStoreExecption(override val message: String) : EventbusException()

    val responseStore: IdGetter<MESSAGE>

    override fun handle(ebMessageHeader: MESSAGE) =
        ebMessageHeader.isResponse && ebMessageHeader.responseId != null

    @Throws(ResponseStoreExecption::class)
    override suspend fun doHandle(ebMessageHeader: MESSAGE, context: SerializersModule) {
        val responseId = ebMessageHeader.responseId!!
        responseStore.removeId(responseId)?.let { ws ->
            ws.response(ebMessageHeader)
        } ?: throw ResponseStoreExecption("did not find a response handler for $ebMessageHeader")
    }
}

interface WSSendable {
    suspend fun send(messageHeader: SendSerializable)
}


fun interface WSErrorLogger {
    fun onError(message: String)
}

interface SendSerializableBuilder<MSGBUILDERKEY : Any> {
    fun toMessageHeader(msgbuilderkey: MSGBUILDERKEY, isResponse: Boolean, responseId: String): SendSerializable
    fun error(
        errorMessage: String,
        msgbuilderkey: MSGBUILDERKEY,
        isResponse: Boolean,
        responseId: String
    ): SendSerializable
}

class EBM2SendSerializableBuilder : SendSerializableBuilder<EBMessageHeader2Builder> {
    override fun toMessageHeader(
        msgbuilderkey: EBMessageHeader2Builder,
        isResponse: Boolean,
        responseId: String
    ): SendSerializable {
        return EBMessageHeader2("EMH2", msgbuilderkey.serialName, null, isResponse, responseId, msgbuilderkey.json)
    }

    override fun error(
        errorMessage: String,
        msgbuilderkey: EBMessageHeader2Builder,
        isResponse: Boolean,
        responseId: String
    ): SendSerializable {
        return EBMessageHeader2(
            "EMH2",
            msgbuilderkey.serialName,
            errorMessage,
            isResponse,
            responseId,
            msgbuilderkey.json
        )
    }

}

interface AnswerOnRequest<MESSAGE : MessageIdable, MSGBUILDERKEY : Any> : WSSendable, WSErrorLogger,
    RequestHolderHandler<MESSAGE> {
    companion object {
        private val logger = KotlinLogging.logger("org.daiv.websocket.mh2.AnswerOnRequest")
    }

    val list: List<RequestResponse<MESSAGE, MSGBUILDERKEY>>

    val sendSerializableBuilder: SendSerializableBuilder<MSGBUILDERKEY>

    override fun handle(ebMessageHeader: MESSAGE) =
        !ebMessageHeader.isResponse && ebMessageHeader.responseId != null

    override suspend fun doHandle(ebMessageHeader: MESSAGE, context: SerializersModule) {
        val responseId = ebMessageHeader.responseId!!
        logger.trace { "handling $responseId" }
        list.find { it.isMessage(ebMessageHeader) }?.let {
            try {
                logger.trace { "find from list: $it" }
                val answer = it.answer(context, ebMessageHeader)
                logger.trace { "got answer: $answer" }
                val x = sendSerializableBuilder.toMessageHeader(answer, true, responseId)
                logger.trace { "send $x" }
                send(x)
            } catch (t: Throwable) {
                val errorMessage = "error thrown when trying to answer -> ${t.message}"
                logger.error { "error: $errorMessage" }
                onError(errorMessage)
                send(sendSerializableBuilder.error(errorMessage, it.errorBuilderKey, true, responseId))
            }
        } ?: run {
            val errorMessage = "did not find a handler for $ebMessageHeader"
            logger.error { errorMessage }
            onError(errorMessage)
        }
    }
}

data class SimpleWSResponder<T : Any>(val serializer: KSerializer<T>, val block: suspend (t: T) -> Unit) :
    WSRequestHandler<EBMessageHeader2> {

    override fun isMessage(messageData: EBMessageHeader2): Boolean {
        return messageData.body == serializer.descriptor.serialName
    }

    override suspend fun answer(messageData: EBMessageHeader2, context: SerializersModule) {
        block(serializer.parse(context, messageData.json))
    }
}

interface MessageChecker<MESSAGE> {
    fun isMessage(messageData: MESSAGE): Boolean
}

interface WSRequestHandler<MESSAGE : MessageIdable> : MessageChecker<MESSAGE> {
    suspend fun answer(messageData: MESSAGE, context: SerializersModule)
}

interface WSResponseAble<MESSAGE : MessageIdable> : RequestHolderHandler<MESSAGE> {
    class WSResponseException(cause: Throwable?, message: String?) : EventbusException(cause, message)

    val wsResponses: List<WSRequestHandler<MESSAGE>>

    override fun handle(ebMessageHeader: MESSAGE) = ebMessageHeader.responseId == null

    @Throws(WSResponseException::class)
    override suspend fun doHandle(ebMessageHeader: MESSAGE, context: SerializersModule) {
        wsResponses.find { it.isMessage(ebMessageHeader) }?.let {
            try {
                it.answer(ebMessageHeader, context)
            } catch (t: Throwable) {
                val msg =
                    "wsResponder: error thrown when trying to execute: $it serializer response: $ebMessageHeader"
                throw WSResponseException(t, msg)
            }
        }
    }
}

class DefaultWSResponsable(
    override val wsResponses: List<WSRequestHandler<EBMessageHeader2>>,
) : WSResponseAble<EBMessageHeader2>

class DefaultWSAnswerable(
    override val list: List<RequestResponse<EBMessageHeader2, EBMessageHeader2Builder>>,
    private val sendable: WSSendable,
    private val errorLogger: WSErrorLogger,
) : AnswerOnRequest<EBMessageHeader2, EBMessageHeader2Builder>, WSSendable by sendable, WSErrorLogger by errorLogger {
    override val sendSerializableBuilder: SendSerializableBuilder<EBMessageHeader2Builder> =
        EBM2SendSerializableBuilder()
}

class DefaultResponseStorable(override val responseStore: IdGetter<EBMessageHeader2>) :
    ResponseStorable<EBMessageHeader2>

interface RequestHolderHandler<MESSAGE : Any> {
    fun handle(messageData: MESSAGE): Boolean
    suspend fun doHandle(messageData: MESSAGE, context: SerializersModule)
}

interface EBSender {
    val sendable: WSSendable
    val responseStore: ResponseStore<EBMessageHeader2>
    val context: SerializersModule

    fun <T : Any> KSerializer<T>.toMessageHeader(
        event: T,
        responseId: String?,
        isResponse: Boolean,
    ): EBMessageHeader2 {
        val message = stringify(event, context)
        return EBMessageHeader2(
            "EMH2",
            descriptor.serialName,
            null,
            isResponse,
            responseId,
            message
        )
    }

    suspend fun <T : Any, R : Any> send(
        serializer: KSerializer<T>,
        t: T,
        responseSerializer: KSerializer<R>,
        response: suspend (R) -> Unit
    ) {
        responseStore.storeTranslator({}) {
            val json = serializer.toMessageHeader(t, it, false)
            sendable.send(json)
        }
    }

    suspend fun <T : Any> send(serializer: KSerializer<T>, t: T) {
        sendable.send(serializer.toMessageHeader(t, null, false))
    }
}


//data class RequestHolder<MESSAGE : MessageIdable>(
//    val context: SerializersModule,
//    val sendable: WSSendable,
//    val responseStore: ResponseStore<MESSAGE>,
//    private val handlers: List<RequestHolderHandler<MESSAGE>>
//) : WSErrorLogger {
//    companion object {
//        private val logger = KotlinLogging.logger { }
////        fun <MESSAGE : MessageIdable, MSGBUILDERKEY : Any> defaults(
////            responseStore: ResponseStore<MESSAGE>,
////            list: List<RequestResponse<MESSAGE, MSGBUILDERKEY>>,
////            wsResponses: List<WSResponder<*>>,
////            sendable: WSSendable,
////            errorLogger: WSErrorLogger
////        ) = listOf(
////            DefaultResponseStorable(responseStore),
////            DefaultWSAnswerable(list, sendable, errorLogger),
////            DefaultWSResponsable(wsResponses)
////        )
//    }
//
////    private val responseStore = ResponseStore(scope)
//
////    private val handlers = listOf<RequestHolderHandler>(
////        DefaultResponseStorable(responseStore, context),
////        DefaultWSAnswerable(list, sendable, this, context),
////        DefaultWSResponsable(wsResponses, context)
////    )
//
//}
