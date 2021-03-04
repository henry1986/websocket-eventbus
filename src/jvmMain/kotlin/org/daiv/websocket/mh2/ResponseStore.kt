package org.daiv.websocket.mh2

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.daiv.time.isoTime
import java.util.concurrent.CancellationException

actual fun timeId() = System.currentTimeMillis().isoTime()

class KtorSender(val websocketSession: WebSocketSession) : WSSendable {
    companion object {
        private val logger = KotlinLogging.logger { }
    }
    override suspend fun send(messageHeader: SendSerializable) {
        logger.trace { "send $messageHeader" }
        websocketSession.outgoing.send(Frame.Text(messageHeader.serialize()))
    }
}

class DMHKtorWebsocketHandler constructor(
    val websocketBuilder: DMHWebsocketBuilder<KtorSender>,
    val onClose: suspend () -> Unit = {}
) : DMHWebsocketInterface by websocketBuilder {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    fun listen(): Job {
        return websocketBuilder.scope.launch(websocketBuilder.coroutineContext) {
            try {
                logger.trace { "starting coroutine websocketbuilder $websocketBuilder" }
                while (true) {
                    val ret = websocketBuilder.sendable.websocketSession.incoming.receive()
                    logger.trace { "received: $ret" }
                    if (ret is Frame.Text) {
                        val text = ret.readText()
                        val header = DoubleMessageHeader.parse(text)
                        logger.trace { "received: $header" }
                        onMessage(header)
                    }
                }
            } catch (cancellationException: CancellationException) {
                onClose()
            } catch (c: ClosedReceiveChannelException){
                onClose()
            }catch (t: Throwable) {
                logger.error(t) { "error in websocketsession" }
            }
        }
    }
}



