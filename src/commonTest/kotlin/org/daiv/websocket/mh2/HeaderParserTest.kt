package org.daiv.websocket.mh2

import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.EmptySerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals

class HeaderParserTest {
    @Serializable
    data class TestData(val x: Int)

    @Test
    fun testTrue() {
        val e = EBMessageHeader2(
            "EMH2",
            TestData.serializer().descriptor.serialName,
            null,
            true,
            "responseID",
            TestData.serializer().stringify(
                TestData(5)
            )
        )
        val parsed = EBMessageHeader2.parse(e.serialize())
        println("h: $parsed")
        val x = TestData.serializer().parse(EmptySerializersModule, parsed.json)
        assertEquals(TestData(5), x)
        assertEquals(e, parsed)
    }
    @Test
    fun testFalse() {
        val e = EBMessageHeader2(
            "EMH2",
            TestData.serializer().descriptor.serialName,
            null,
            false,
            "responseID",
            TestData.serializer().stringify(
                TestData(5)
            )
        )
        val parsed = EBMessageHeader2.parse(e.serialize())
        println("h: $parsed")
        val x = TestData.serializer().parse(EmptySerializersModule, parsed.json)
        assertEquals(TestData(5), x)
        assertEquals(e, parsed)
    }

}