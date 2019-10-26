package org.daiv.websocket

import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import mu.KLogging
import org.daiv.util.DefaultRegisterer
import org.daiv.util.Registerer
import org.daiv.websocket.*
import java.util.*
import kotlin.reflect.KClass


class ControlledChannelNotifier(val registerer: DefaultRegisterer<ControlledChannel> = DefaultRegisterer()) :
    Registerer<ControlledChannel> by registerer, ControlledChannel {
    override val farmName: String
        get() = registerer.firstOrNull()?.farmName ?: "there is no known farm, as there is no channel registered"

    override fun toWSEnd(event: Message<Any, Any>) {
        registerer.forEach {
            it.toWSEnd(event)
        }
    }
}

class ControlledChannelAdapter(val controlledChannel: ControlledChannel, private val messageHeader: ForwardedMessage) :
    ControlledChannel {
    override val farmName = messageHeader.farmName
    override fun toWSEnd(event: Message<Any, Any>) = controlledChannel.toWSEnd(Message(messageHeader, event.e))
}

class ControlledChannelImpl(private val sendChannel: SendChannel<Frame>, override val farmName: String) :
    ControlledChannel {
    companion object : KLogging()


    override fun toWSEnd(event: Message<Any, Any>) {
        GlobalScope.launch {
            try {
                val eb = toJSON(event)
                logger.trace { "event send to Frontend: $event" }
                val string = eb.serialize()
                logger.debug { "string send to Frontend: $string" }
                sendChannel.send(Frame.Text(string))
            } catch (t: Throwable) {
                logger.error(t) { "error at sending $event" }
            }
        }
    }
}

fun Message<out Any, out WSEvent>?.reqString(event: WSEvent) = this?.let {
    val header = it.messageHeader
    header as FrontendMessageHeader
    "response-${header.messageId}"
} ?: "${event::class.simpleName}-${Date()}"

interface MessageHandler {
    val controlledChannel: ControlledChannel
    fun toWSEnd(event: Message<Any, Any>) = controlledChannel.toWSEnd(event)
    fun toFrontend(frontendMessageHeader: FrontendMessageHeader, event: WSEvent) {
        toWSEnd(Message(frontendMessageHeader, event))
    }

    fun toFrontend(event: WSEvent, req: Message<out Any, out WSEvent>? = null) {
        toFrontend(FrontendMessageHeader(controlledChannel.farmName, false, req.reqString(event)), event)
    }

    fun toFrontendFromRemote(remoteName: String, event: WSEvent, req: Message<out Any, out WSEvent>? = null) =
        toFrontend(FrontendMessageHeader(remoteName, true, req.reqString(event)), event)
}

interface MessageReceiver<T : WSEvent> {
    suspend fun onMessage(t: T)
}

//interface TestEvent : WSEvent
//class TestReceiver : MessageReceiver<TestEvent>
//
//fun example() {
//    SessionHandlerManager(ControlledChannelImpl(), mapOf(TestEvent::class to TestReceiver()))
//}

class SessionHandlerManager(
    override val controlledChannel: ControlledChannel,
    val map: Map<KClass<out WSEvent>, MessageReceiver<out WSEvent>>
) : SessionHandler {

    override suspend fun frontEndMessage(message: Message<out Any, out WSEvent>): SessionHandler {
        val event = message.e
        val r = map.filter { it.key.isInstance(event) }.values.firstOrNull()
        if (r == null) {
            throw RuntimeException("no MessageHandler for event: $event found")
        } else {
            r as MessageReceiver<WSEvent>
            r.onMessage(event)
        }
        return this
    }

}

interface SessionHandler : MessageHandler {

    fun shallClose() = false

    /**
     * returns next SessionHandler
     */
    suspend fun frontEndMessage(message: Message<out Any, out WSEvent>): SessionHandler


    fun onInit() {}

    fun onClose() {}

}
