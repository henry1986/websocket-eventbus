package org.daiv.websocket

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.daiv.util.DefaultRegisterer
import org.daiv.util.Registerer

interface SealedTest

@Serializable
object Sealed1 : SealedTest

@Serializable
data class SealedData(val x: Int) : SealedTest

@Serializable
data class MySealedObject(@Polymorphic val sealedTest: SealedTest, @Polymorphic val sealedData: SealedTest?, val s: String)

@Serializable
data class MapKey(val x: Int, val s: String)

@Serializable
data class MapValue(val b: String, val d: Long)

@Serializable
data class MapHolder(val x: Int, val map: Map<MapKey, MapValue>)

val sModule = SerializersModule {
    polymorphic(SealedTest::class) {
        subclass(Sealed1::class)
        subclass(SealedData::class)
//        Sealed1::class with Sealed1.serializer()
//        SealedData::class with SealedData.serializer()
    }
}

data class TestSender(
    private val registerer: DefaultRegisterer<DataReceiver> = DefaultRegisterer()
) : DataSender,
    Registerer<DataReceiver> by registerer {
    override fun send(message: String) {

        registerer.event { runBlocking { receive(message) } }
    }
}
