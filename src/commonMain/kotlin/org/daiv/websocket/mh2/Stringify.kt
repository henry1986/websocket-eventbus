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


