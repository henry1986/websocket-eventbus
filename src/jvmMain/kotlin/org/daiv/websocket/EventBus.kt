package org.daiv.websocket

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import mu.KLogging

fun EBMessageHeader.parse(): Message<out Any, out Any> {
    EventBusReceiver.logger.trace { "parse: $this" }
    EventBusReceiver.logger.trace { body }
    val headerClazz = Class.forName(header)
    val bodyClazz = Class.forName(body)
    val x = Gson().fromJson(json, JsonObject::class.java)
    val m = x.get("messageHeader")
    val e = x.get("e")
    val header = Gson().fromJson(m, headerClazz)
    val body = Gson().fromJson(e, bodyClazz)

    return Message(header, body)
}

//fun <HEADER : Any, BODY : Any> toMessage(serializer: KSerializer<HEADER>, bodySerializer: KSerializer<BODY>,
//                                         header: HEADER, body: BODY): String {
//    val s = Message.serializer(serializer, bodySerializer)
//    val e = Message(header, body)
//    return JSON.nonstrict.stringify(s, e)
//}
//
//
//@ImplicitReflectionSerializer
//inline fun <reified T : Any> toImplJSON(any: T): EBMessageHeader {
//    val serializer = T::class.serializer()
//    return EBMessageHeader(FrontendMessageHeader.serializer().descriptor.name, serializer.descriptor.name,
//                           toMessage(FrontendMessageHeader.serializer(), serializer, FrontendMessageHeader(""), any))
//}

fun Message<Any, Any>.toJSON(): EBMessageHeader {
    val json = Gson().toJson(this)
    return EBMessageHeader(messageHeader::class.qualifiedName!!, e::class.qualifiedName!!, json)
}

class EventBusReceiver(
    private val incoming: ReceiveChannel<Frame>,
    initHandler: SessionHandler,
    val closeCheck: () -> Boolean = { false }
) {
    companion object : KLogging()

    val newSingleThreadContext = newSingleThreadContext("EventBusReceiverThreadContext")

    private var sessionHandler: SessionHandler = initHandler

    private tailrec suspend fun incomingConsume() {
        val frame = incoming.receive()
        sessionHandler = when {
            frame is Frame.Text -> {
                if (closeCheck()) {
                    return
                }
                val readText = frame.readText()
                logger.debug { "we got message: $readText" }
                val parse = EBMessageHeader.parse(readText)
                val e = parse.parse()
                withContext(newSingleThreadContext) {
                    try {
                        sessionHandler.frontEndMessage(e as Message<out Any, out WSEvent>)
                    } catch (t:Throwable){
                        logger.error(t) { "catched exception when analyzing frontendmessage" }
                        sessionHandler
                    }
                }
            }
            else -> sessionHandler
        }
        if (sessionHandler.shallClose()) {
            return
        }
        incomingConsume()
    }

    suspend fun handle(c: ControlledChannel) {
        sessionHandler.onInit(c)
        try {
            incomingConsume()
        } catch (throwable: Throwable) {
            logger.error("incoming consume throws: ", throwable)
        } finally {
            logger.info { "websocket closed" }
            sessionHandler.onClose(c)
        }
    }
}
