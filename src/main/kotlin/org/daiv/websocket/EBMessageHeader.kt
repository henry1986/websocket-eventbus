package org.daiv.websocket

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON

interface WSEvent

@Serializable
data class FrontendMessageHeader constructor(val farmName: String, val isRemote: Boolean)

@Serializable
data class ForwardedMessage constructor(val id:String, val farmName:String)


interface ControlledChannel {
    val farmName: String
    fun toWSEnd(event: Message<Any, Any>)
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