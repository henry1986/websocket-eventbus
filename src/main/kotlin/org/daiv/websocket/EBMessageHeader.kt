package org.daiv.websocket

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON

interface WSEvent

internal expect fun dateString():String

@Serializable
data class FrontendMessageHeader constructor(val farmName: String, val isRemote: Boolean, val messageId:String)

@Serializable
data class ForwardedMessage constructor(val id:String, val farmName:String)


fun <T : Any> toJSON(
    serializer: KSerializer<T>,
    event: T,
    req: FrontendMessageHeader? = null
): EBMessageHeader {
    val resString = req?.messageId ?: "${serializer.descriptor.name}-${dateString()}"
    return EBMessageHeader(
        FrontendMessageHeader.serializer().descriptor.name, serializer.descriptor.name,
        toMessage(
            FrontendMessageHeader.serializer(),
            serializer,
            FrontendMessageHeader("", false, resString),
            event
        )
    )
}

fun <HEADER : Any, BODY : Any> toMessage(
    serializer: KSerializer<HEADER>, bodySerializer: KSerializer<BODY>,
    header: HEADER, body: BODY
): String {
    val s = Message.serializer(serializer, bodySerializer)
    val e = Message(header, body)
    return JSON.nonstrict.stringify(s, e)
}

@Serializable
data class Message<T : Any, E : Any>(val messageHeader: T, val e: E)

//fun <HEADER : Any, BODY : Any> serialize(serializer: KSerializer<HEADER>, bodySerializer: KSerializer<BODY>,
//                                            header: HEADER, body: BODY): String {
//    val s = Message.serializer(serializer, bodySerializer)
//    val e = Message(header, body)
//    return JSON.nonstrict.stringify(s, e)
//}


data class EBMessageHeader(val header:String, val body:String, val json:String){

    fun serialize():String{
        return "[$header, $body, $json]"
    }

    companion object {
        fun parse(string: String):EBMessageHeader{
            val trim = string.trim()
            if (!trim.startsWith("[")) {

            }
            val removedBrackets = trim.substring(1, trim.length - 1)
            val split = removedBrackets.split(",")
            val header = split[0].trim()
            val body = split[1].trim()
            val last = split.drop(2).joinToString (",")
            return EBMessageHeader(header, body, last)
        }
    }
}