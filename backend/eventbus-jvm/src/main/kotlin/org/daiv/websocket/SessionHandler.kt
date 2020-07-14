package org.daiv.websocket

import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptyModule
import kotlinx.serialization.modules.SerialModule
import mu.KLogging
import mu.KotlinLogging
import org.daiv.util.DefaultRegisterer
import org.daiv.util.Registerer
import java.util.*
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger {}

interface ControlledChannel {
    val farmName: String
    fun toWSEnd(event: Message<Any, Any>) {
        val eb = event.toJSON()
        ControlledChannelImpl.logger.trace { "event send to Frontend: $event" }
        toWSEndSerializer(eb)
    }

    fun toWSEndSerializer(ebMessageHeader: EBMessageHeader)
    fun toFrontend(ebMessageHeader: EBMessageHeader) = toWSEndSerializer(ebMessageHeader)
    fun <T : Any> toFrontend(serializer: KSerializer<T>, t: T, context: SerialModule = EmptyModule) =
        toFrontend(toJSON(serializer, t, context = context))
}

class ControlledChannelNotifier(val registerer: DefaultRegisterer<ControlledChannel> = DefaultRegisterer()) :
    Registerer<ControlledChannel> by registerer, ControlledChannel {
    override val farmName: String
        get() = registerer.firstOrNull()?.farmName ?: "there is no known farm, as there is no channel registered"

    override fun toWSEnd(event: Message<Any, Any>) {
        registerer.forEach {
            it.toWSEnd(event)
        }
    }

    override fun toWSEndSerializer(ebMessageHeader: EBMessageHeader) {
        throw RuntimeException("calling not possible")
    }
}

class ControlledChannelAdapter(val controlledChannel: ControlledChannel, private val messageHeader: ForwardedMessage) :
    ControlledChannel {
    override val farmName = messageHeader.farmName
    override fun toWSEnd(event: Message<Any, Any>) = controlledChannel.toWSEnd(Message(messageHeader, event.e))
    override fun toWSEndSerializer(ebMessageHeader: EBMessageHeader) {
        throw RuntimeException("calling not possible")
    }
}

class ControlledChannelImpl(private val sendChannel: SendChannel<Frame>, override val farmName: String) :
    ControlledChannel {
    companion object : KLogging()

    override fun toWSEndSerializer(ebMessageHeader: EBMessageHeader) {
        GlobalScope.launch {
            try {
                val string = ebMessageHeader.serialize()
                logger.debug { "string send to Frontend: $string" }
                sendChannel.send(Frame.Text(string))
            } catch (t: Throwable) {
                logger.error(t) { "error at sending $ebMessageHeader" }
            }
        }
    }
}

fun Message<out Any, out WSEvent>?.reqString(event: WSEvent) = this?.let {
    val header = it.messageHeader
    header as FrontendMessageHeader
    "response-${header.messageId}"
} ?: "${event::class.simpleName}-${Date()}"

interface ControlledChannelListener{
    fun onMessageListener(controlledChannel: ControlledChannel) {
    }

    fun removeMessageListener(controlledChannel: ControlledChannel) {

    }
}

interface MessageSender {
    val controlledChannel: ControlledChannel
    val context: SerialModule
        get() = EmptyModule


    fun toWSEnd(event: Message<Any, Any>) = controlledChannel.toWSEnd(event)
    fun toFrontend(frontendMessageHeader: FrontendMessageHeader, event: WSEvent) {
        toWSEnd(Message(frontendMessageHeader, event))
    }

    fun <T : Any> toFrontend(serializer: KSerializer<T>, t: T) =
        controlledChannel.toFrontend(serializer, t, context = context)

    fun toFrontend(event: WSEvent, req: Message<out Any, out WSEvent>? = null) {
        toFrontend(FrontendMessageHeader(controlledChannel.farmName, false, req.reqString(event)), event)
    }


    fun toFrontendFromRemote(remoteName: String, event: WSEvent, req: Message<out Any, out WSEvent>? = null) =
        toFrontend(FrontendMessageHeader(remoteName, true, req.reqString(event)), event)
}


interface TestEvent : WSEvent

class TestReceiver : MessageReceiver<TestEvent> {
    override suspend fun onMessage(event: TestEvent) {
    }
}

fun example() {
    SessionHandlerManager(mapOf(TestEvent::class to TestReceiver()))
}

class SessionHandlerManager(val map: Map<KClass<out WSEvent>, MessageReceiver<out WSEvent>>) : SessionHandler {

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

    fun register(controlledChannel: ControlledChannel) {
        map.values.forEach {
            if (it is ControlledChannelListener) {
                it.onMessageListener(controlledChannel)
            }
        }
    }

    fun unregister(controlledChannel: ControlledChannel) {
        map.values.forEach {
            if (it is ControlledChannelListener) {
                it.removeMessageListener(controlledChannel)
            }
        }
    }
}
