package org.daiv.websocket.mh2

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule


fun <BODY : Any> KSerializer<BODY>.stringify(
    body: BODY,
    context: SerializersModule = EmptySerializersModule
): String {
    val json = Json {
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
        isLenient = true
        serializersModule = context
    }
    return json.encodeToString(this, body)
}

fun <T : Any> KSerializer<T>.parse(context: SerializersModule, string: String): T {
    val jsonParser = Json {
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
        isLenient = true
        serializersModule = context
    }
    return jsonParser.decodeFromString(this, string)
}

fun <T : Any> KSerializer<T>.toMessageHeader(
    event: T,
    responseId: String?,
    isResponse: Boolean,
    context: SerializersModule = EmptySerializersModule
): EBMessageHeader2 {
    val message = stringify(event, context)
    return EBMessageHeader2(
        "EMH2",
        descriptor.serialName,
        null,
        isResponse,
        responseId,
        message
    )
}

fun <T : Any> KSerializer<T>.error(
    errorMessage:String,
    responseId: String,
    isResponse: Boolean,
): EBMessageHeader2 {
    return EBMessageHeader2(
        "EMH2",
        descriptor.serialName,
        errorMessage,
        isResponse,
        responseId,
        "null"
    )
}
