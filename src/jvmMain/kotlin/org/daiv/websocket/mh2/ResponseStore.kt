package org.daiv.websocket.mh2

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.daiv.coroutines.DefaultScopeContextable
import org.daiv.coroutines.ScopeContextable
import org.daiv.time.isoTime
import java.util.concurrent.CancellationException

actual fun timeId() = System.currentTimeMillis().isoTime()

class KtorSender(val websocketSession: WebSocketSession, val scopeContextable: ScopeContextable = DefaultScopeContextable()) : WSSendable {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun send(messageHeader: SendSerializable) {
        logger.trace { "send $messageHeader" }
        websocketSession.outgoing.send(Frame.Text(messageHeader.serialize()))
    }

    override suspend fun receive(messageHandler: MessageHandler){
        val launch = scopeContextable.launch("ktor sender receiving") {
            try {
                while (true) {
                    val ret = websocketSession.incoming.receive()
                    logger.trace { "received: $ret" }
                    if (ret is Frame.Text) {
                        val text = ret.readText()
                        messageHandler.onText(text)
                    }
                }
            } catch (cancellationException: CancellationException) {
                messageHandler.onClose("close because of cancellation")
            } catch (c: ClosedReceiveChannelException) {
                messageHandler.onClose("closed because of closedReceived")
            } catch (t: Throwable) {
                messageHandler.onError("error: $t, ${t.stackTrace}")
                logger.error(t) { "error in websocketsession" }
            }
        }
        launch.join()
    }

    override fun isActive() = websocketSession.isActive
}

typealias DMHKtorWebsocketHandler = KtorWebsocketHandler<DoubleMessageHeader, DMHSerializableKey>

class KtorWebsocketHandler<MESSAGE, MSGBUILDERKEY : Any> constructor(
    val websocketBuilder: WebsocketBuilder< MESSAGE, MSGBUILDERKEY>,
    val onClose: suspend () -> Unit = {}
) : WebsocketInterface<MESSAGE> by websocketBuilder
        where MESSAGE : MessageIdable,
              MESSAGE : SendSerializable {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    suspend fun listen() {
        websocketBuilder.sendable.receive(object:MessageHandler{
            override fun onOpen(openInfo:String?) {
            }

            override fun onClose(closeInfo:String?) {
                websocketBuilder.scopeContextable.launch("closing") {
                    logger.info { "closing websocket: $closeInfo" }
                    this@KtorWebsocketHandler.onClose()
                }
            }

            override fun onError(errorInfo:String?) {
                websocketBuilder.scopeContextable.launch("error") {
                    logger.error { "error in websocket: $errorInfo" }
                    this@KtorWebsocketHandler.onClose()
                }
            }

            override fun onText(text: String) {
                val header = websocketBuilder.messageFactory.parse(text)
                logger.trace { "received: $header" }
                websocketBuilder.scopeContextable.launch("got text $text coroutine") {
                    onMessage(header)
                }
            }
        })
    }
}



