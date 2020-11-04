package org.daiv.websocket

import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import mu.KLogging
import mu.KotlinLogging
import org.daiv.util.DefaultRegisterer
import org.daiv.util.Registerer
import org.slf4j.Marker
import java.util.*
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger {}

interface ControlledChannel {
    val farmName: String
    val notLoggingFilterList: List<String>
        get() = emptyList()

    fun toWSEnd(event: Message<Any, Any>, marker: Marker? = null) {
        val eb = event.toJSON()
        toWSEndSerializer(eb, marker)
    }

    fun toWSEndSerializer(ebMessageHeader: EBMessageHeader, marker: Marker? = null)
    fun toFrontend(ebMessageHeader: EBMessageHeader, marker: Marker? = null) = toWSEndSerializer(ebMessageHeader, marker)
    fun <T : Any> toFrontend(
        serializer: KSerializer<T>,
        t: T,
        context: SerializersModule = EmptySerializersModule,
        marker: Marker?
    ) {
        if (!notLoggingFilterList.contains(serializer.descriptor.serialName)) {
            ControlledChannelImpl.logger.debug { "event send to Frontend: $t" }
        }
        toFrontend(toJSON(serializer, t, context = context), marker)
    }
}

class ControlledChannelNotifier(
    val registerer: DefaultRegisterer<ControlledChannel> = DefaultRegisterer(),
    override val notLoggingFilterList: List<String> = emptyList()
) :
    Registerer<ControlledChannel> by registerer, ControlledChannel {
    override val farmName: String
        get() = registerer.firstOrNull()?.farmName ?: "there is no known farm, as there is no channel registered"

    override fun toWSEnd(event: Message<Any, Any>, marker: Marker?) {
        registerer.forEach {
            it.toWSEnd(event, marker)
        }
    }

    override fun toWSEndSerializer(ebMessageHeader: EBMessageHeader, marker: Marker?) {
        throw RuntimeException("calling not possible")
    }
}

class ControlledChannelAdapter(val controlledChannel: ControlledChannel, private val messageHeader: ForwardedMessage) :
    ControlledChannel {
    override val farmName = messageHeader.farmName
    override val notLoggingFilterList: List<String>
        get() = controlledChannel.notLoggingFilterList

    override fun toWSEnd(event: Message<Any, Any>, marker: Marker?) =
        controlledChannel.toWSEnd(Message(messageHeader, event.e), marker)

    override fun toWSEndSerializer(ebMessageHeader: EBMessageHeader, marker: Marker?) {
        throw RuntimeException("calling not possible")
    }
}

class ControlledChannelImpl(
    private val sendChannel: SendChannel<Frame>,
    override val farmName: String,
    override val notLoggingFilterList: List<String> = emptyList()
) : ControlledChannel {
    companion object : KLogging()

    override fun toWSEndSerializer(ebMessageHeader: EBMessageHeader, marker: Marker?) {
        GlobalScope.launch {
            try {
                val string = ebMessageHeader.serialize()
                logger.debug(marker) { "string send to Frontend: $string" }
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

interface ControlledChannelListener {
    fun onMessageListener(controlledChannel: ControlledChannel) {
    }

    fun removeMessageListener(controlledChannel: ControlledChannel) {

    }
}

interface MessageSender {
    val controlledChannel: ControlledChannel
    val context: SerializersModule
        get() = EmptySerializersModule


    fun toWSEnd(event: Message<Any, Any>, marker: Marker? = null) = controlledChannel.toWSEnd(event, marker)
    fun toFrontend(frontendMessageHeader: FrontendMessageHeader, event: WSEvent, marker: Marker? = null) {
        toWSEnd(Message(frontendMessageHeader, event), marker)
    }

    fun <T : Any> toFrontend(serializer: KSerializer<T>, t: T, marker: Marker? = null) {
        controlledChannel.toFrontend(serializer, t, context = context, marker)
    }

    fun toFrontend(event: WSEvent, req: Message<out Any, out WSEvent>? = null, marker: Marker? = null) {
        toFrontend(FrontendMessageHeader(controlledChannel.farmName, false, req.reqString(event)), event, marker)
    }


    fun toFrontendFromRemote(
        remoteName: String,
        event: WSEvent,
        req: Message<out Any, out WSEvent>? = null,
        marker: Marker? = null
    ) =
        toFrontend(FrontendMessageHeader(remoteName, true, req.reqString(event)), event, marker)
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
