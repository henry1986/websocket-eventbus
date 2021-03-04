package org.daiv.websocket.mh2

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import mu.KotlinLogging
import org.daiv.websocket.MessageHeaderInterface

data class EBMessageHeader2Builder(val serialName: String, val json: String)
interface SimpleRequestResponse<REQUEST : Any, RESPONSE : Any> :
    RequestResponse<EBMessageHeader2, EBMessageHeader2Builder> {
    val serializer: KSerializer<REQUEST>
    val response: KSerializer<RESPONSE>

    override suspend fun answer(context: SerializersModule, message: EBMessageHeader2): EBMessageHeader2Builder {
        return EBMessageHeader2Builder(
            response.descriptor.serialName,
            response.stringify(onMessage(serializer.parse(context, message.json)), context)
        )
    }

    override fun isMessage(messageData: EBMessageHeader2): Boolean = messageData.body == serializer.serialName()

    override val errorBuilderKey: EBMessageHeader2Builder
        get() = EBMessageHeader2Builder(response.descriptor.serialName, "")

    suspend fun onMessage(request: REQUEST): RESPONSE
}

interface RequestResponse<MESSAGE : MessageIdable, MSGBUILDERKEY : Any> : MessageChecker<MESSAGE> {
    suspend fun answer(context: SerializersModule, message: MESSAGE): MSGBUILDERKEY
    val errorBuilderKey: MSGBUILDERKEY
//    suspend fun onMessage(request: REQUEST): RESPONSE
}


data class HeaderParser(val rest: String, val list: List<String> = emptyList()) {

    private fun indexesOf(string: String, char: Char) =
        string.mapIndexed { index, c -> index to c }.filter { it.second == char }.map { it.first }

    fun takeAll() = HeaderParser(rest = "", list + rest.trim().replaceFirst("json = ", ""))

    fun parseNoString(): HeaderParser {
        val next = rest.split(",")[0].trim()
        return HeaderParser(rest.dropWhile { it != ',' }, list + next)
    }

    fun parseNextString(): HeaderParser {
        val string = rest
        val i = indexesOf(string, '\\')
        val i2 = indexesOf(string, '"')
        val start = i2[0]
        val i3 = i2.drop(1).dropWhile { i.contains(it - 1) }
        val res = string.substring(start + 1, i3.first())
        val next = string.drop(i3.first() + 1)
//        println("i: $i")
//        println("i2: $i2")
//        println("i3: $i3")
//        println("res: $res")
//        println("next: $next")
        return HeaderParser(next, list + res)
    }

    fun removeComma(): HeaderParser {
        val first = rest.trim().first()
        if (first != ',') {
            throw RuntimeException(
                "received impossible value: $first - expected a comma instead: $rest"
            )
        }
        return HeaderParser(rest.trim().drop(1), list)
    }

    companion object {
        fun headerParsed(toParse: String) = HeaderParser(toParse.trim().let { it.substring(1, it.length - 1) })
    }
}

interface SendSerializable {
    fun serialize(): String
}

interface MessageIdable {
    val isResponse: Boolean
    val responseId: String?
}

interface MessageData : MessageIdable {
    val body: String
    val errorMessage: String?
    val json: String
}

interface MessageBuilder {
    fun build(errorMessage: String?, isResponse: Boolean, responseId: String?, json: String)
}

data class EBMessageHeader2 constructor(
    val header: String,
    override val body: String,
    override val errorMessage: String?,
    override val isResponse: Boolean,
    override val responseId: String?,
    override val json: String
) : MessageData, MessageIdable, SendSerializable {

    override fun serialize() = "[\"$header\", \"$body\", \"$errorMessage\", $isResponse, \"$responseId\", json = $json]"

    companion object {
        private val logger = KotlinLogging.logger("org.daiv.websocket.mh2.EBMessageHeader2")
        private fun String.toNull() = if (this == "null") null else this

        fun parse(string: String): EBMessageHeader2 {
            val list = HeaderParser.headerParsed(string).parseNextString().removeComma().parseNextString().removeComma()
                .parseNextString().removeComma().parseNoString().removeComma().parseNextString().removeComma()
                .takeAll().list
            return EBMessageHeader2(
                list.first(),
                list[1],
                list[2].toNull(),
                list[3].toBoolean(),
                list[4].toNull(),
                list[5]
            )
        }
    }
}
