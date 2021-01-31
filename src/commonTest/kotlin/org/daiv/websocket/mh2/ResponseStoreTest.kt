package org.daiv.websocket.mh2

import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

expect fun runTest(block: suspend () -> Unit)

class ResponseStoreTest {

    @Serializable
    data class ToStore1(val x: Int)

    @Test
    fun testIDGenerator() {
        val set = setOf("a")
        val x = IDGenerator { set.contains(it) }
        val id = x.getId("a")
        assertEquals("a-1", id)
    }

    @Test
    fun test() = runTest {
        val store = ResponseStore<EBMessageHeader2>()
        val channel = Channel<String>()
        val body = ToStore1.serializer().stringify(ToStore1(5))
        val serialName = ToStore1.serializer().descriptor.serialName
        val header = EBMessageHeader2("EMH2", serialName, null, true, "resId1", body)
        store.storeTranslator({}, {
            channel.send(it.serialize())
        }) {
            store.removeId(it)?.let {
                it.response(header)
            } ?: fail("did not find $it")
        }
        val ret = channel.receive()
        assertEquals(header.serialize(), ret)
        store.cancel()
    }

    @Serializable
    data class Including(val x: Int)

    @Serializable
    data class IncludingS<T : Any>(val z: Int, val t: T)

    @Test
    fun testDoubleSerializer() {
        val name = IncludingS.serializer(Including.serializer()).descriptor.serialName
        println("name: $name")
    }
}