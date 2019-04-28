package org.daiv.websocket

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JSON

fun <HEADER : Any, BODY : Any> toMessage(serializer: KSerializer<HEADER>, bodySerializer: KSerializer<BODY>,
                                         header: HEADER, body: BODY): String {
    val s = Message.serializer(serializer, bodySerializer)
    val e = Message(header, body)
    return JSON.nonstrict.stringify(s, e)
}

inline fun <reified T : Any> toJSON(serializer: KSerializer<T>, event: T): EBMessageHeader {
    println(event)
    return EBMessageHeader(FrontendMessageHeader.serializer().descriptor.name, serializer.descriptor.name,
        toMessage(FrontendMessageHeader.serializer(), serializer, FrontendMessageHeader("", false),
            event))
}

fun <T : Any> toName(serializer: KSerializer<T>) = serializer.descriptor.name


fun <T : Any> EBMessageHeader.toMessage(serializer: KSerializer<T>): Message<FrontendMessageHeader, T> {
    return JSON.parse(Message.serializer(FrontendMessageHeader.serializer(), serializer), json)
}

fun <T : Any> EBMessageHeader.toObject(serializer: KSerializer<T>): T {
    return toMessage(serializer).e
}

class Translater<T : Any>(val serializer: KSerializer<T>, val fct: (T) -> Unit) {
    fun call(messageHeader: EBMessageHeader, func: (FrontendMessageHeader) -> Unit): Boolean {
        if (messageHeader.body == toName(serializer)) {
            val message = messageHeader.toMessage(serializer)
            func(message.messageHeader)
            fct(message.e)
            return true
        }
        return false
    }
}

data class TranslaterList(val list: List<Translater<out WSEvent>> = emptyList(),
                          val headerFunc: (FrontendMessageHeader) -> Unit) {
    fun <T : WSEvent> append(serializer: KSerializer<T>, func: (T) -> Unit) =
        copy(list = list + Translater(serializer, func))

    fun run(messageHeader: EBMessageHeader): Boolean {
        return list.takeWhile { !it.call(messageHeader, headerFunc) }.size != list.size
    }
}
