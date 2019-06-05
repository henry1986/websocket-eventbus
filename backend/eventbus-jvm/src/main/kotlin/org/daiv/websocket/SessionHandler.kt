package org.daiv.websocket

import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import mu.KLogging
import org.daiv.util.DefaultRegisterer
import org.daiv.util.Registerer
import org.daiv.websocket.*


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


interface SessionHandler {
    val controlledChannel: ControlledChannel

    fun shallClose() = false

    /**
     * returns next SessionHandler
     */
    suspend fun frontEndMessage(message: Message<out Any, out WSEvent>): SessionHandler

    fun toWSEnd(event: Message<Any, Any>) = controlledChannel.toWSEnd(event)

    fun onInit() {}

    fun onClose() {}

    fun toFrontend(frontendMessageHeader: FrontendMessageHeader, event: WSEvent) {
        toWSEnd(Message(frontendMessageHeader, event))
    }

    fun toFrontend(event: WSEvent) {
        toFrontend(FrontendMessageHeader(controlledChannel.farmName, false), event)
    }

    fun toFrontendFromRemote(remoteName: String, event: WSEvent) = toFrontend(
            FrontendMessageHeader(remoteName, true), event)
}
