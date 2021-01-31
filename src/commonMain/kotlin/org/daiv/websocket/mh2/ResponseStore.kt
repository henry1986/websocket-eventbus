package org.daiv.websocket.mh2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import mu.KotlinLogging
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


internal expect fun timeId(): String

fun interface IDGenerator {
    fun hasID(id: String): Boolean
    fun getId(id: String): String {
        if (hasID(id)) {
            return getId("$id-1")
        }
        return id
    }
}

class ResponseStore<MESSAGE:Any>(
    val scope: CoroutineScope = GlobalScope,
    val coroutineContext: CoroutineContext = EmptyCoroutineContext
) : IDGenerator, IdGetter <MESSAGE>{
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val map = mutableMapOf<String, WSResponse<MESSAGE>>()

    override fun hasID(id: String): Boolean = map.containsKey(id)

    private val channel = Channel<ResponseStoreEvent<MESSAGE>>()

    interface ResponseStoreEvent<MESSAGE:Any>

    data class TranslatorRequest<MESSAGE:Any>(val wsResponse: WSResponse<MESSAGE>, val callback: suspend (String) -> Unit):ResponseStoreEvent<MESSAGE>
    data class Remove<MESSAGE:Any>(val id: String):ResponseStoreEvent<MESSAGE>

    private val job = scope.launch(coroutineContext) {
        while (true) {
            val r = channel.receive()
            logger.trace { "received: $r" }
            when (r) {
                is TranslatorRequest -> storeResponseTranslator(r)
                is Remove -> map.remove(r.id)
            }
        }
    }

    fun cancel() {
        job.cancel()
    }

    override suspend fun removeId(responseId: String): WSResponse<MESSAGE>? {
        val ret = map[responseId]
        channel.send(Remove(responseId))
        return ret
    }

    private suspend fun storeResponseTranslator(translatorRequest: TranslatorRequest<MESSAGE>) {
        val id = getId(timeId())
        map[id] = translatorRequest.wsResponse
        scope.launch(coroutineContext) {
            translatorRequest.callback(id)
        }
    }

    data class WSResponse<MESSAGE : Any>(
        val onError: (String) -> Unit,
        val response: suspend (MESSAGE) -> Unit
    )

    suspend fun storeTranslator(
        onError: (String) -> Unit,
        response: suspend (MESSAGE) -> Unit,
        callback: suspend (String) -> Unit
    ) {
        channel.send(TranslatorRequest(WSResponse(onError, response), callback))
    }
}



