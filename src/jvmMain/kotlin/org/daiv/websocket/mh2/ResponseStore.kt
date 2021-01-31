package org.daiv.websocket.mh2

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.EmptySerializersModule
import org.daiv.time.isoTime
import kotlin.coroutines.CoroutineContext

actual fun timeId() = System.currentTimeMillis().isoTime()

//class KtorSender(val outgoing: SendChannel<Frame>) : WSSendable {
//    override suspend fun send(messageHeader: SendSerializable) {
//        outgoing.send(Frame.Text(messageHeader.serialize()))
//    }
//}
//
//class KtorWebsocketHandler<MESSAGE:MessageIdable> private constructor(
//    outgoing: SendChannel<Frame>,
//    private val scope: CoroutineScope,
//    private val context: CoroutineContext,
//    responder: List<WSResponder<MESSAGE>>,
//    requestResponses: List<RequestResponse<*, *>>,
//    errorLogger: WSErrorLogger,
//    sender: KtorSender = KtorSender(outgoing),
//    store: ResponseStore<MESSAGE> = ResponseStore(scope, context),
//    private val holder: RequestHolder<MESSAGE> = RequestHolder(
//        EmptySerializersModule,
//        sender,
//        store,
//        RequestHolderHandler.defaults(store, requestResponses, responder, sender, errorLogger)
//    ),
//) : EBSender by holder {
//    constructor(
//        outgoing: SendChannel<Frame>,
//        scope: CoroutineScope,
//        context: CoroutineContext,
//        responder: List<WSResponder<MESSAGE>>,
//        requestResponses: List<RequestResponse<*, *>>,
//        errorLogger: WSErrorLogger,
//    ) : this(outgoing, scope, context, responder, requestResponses, errorLogger, KtorSender((outgoing)))
//
//    suspend fun run(incoming: ReceiveChannel<Frame>): Job {
//        return scope.launch(context) {
//            while (true) {
//                val ret = incoming.receive()
//                if (ret is Frame.Text) {
//                    val text = ret.readText()
//                    val header = EBMessageHeader2.parse(text)
//                    holder.onMessage(header)
//                }
//            }
//        }
//    }
//}

