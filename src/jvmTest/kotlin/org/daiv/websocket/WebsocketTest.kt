package org.daiv.websocket

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import kotlin.test.Test
import kotlin.test.assertEquals

class WebsocketTest {
    val logger = KotlinLogging.logger {}

    class CalledTest {
        fun call() {

        }
    }

    @Test
    fun testSealed() {
        val check = MySealedObject(Sealed1, SealedData(5), "Wow")
        val json = Json { serializersModule = sModule }
        val jsonObject = json.encodeToString(MySealedObject.serializer(), check)
        val parsed = json.decodeFromString(MySealedObject.serializer(), jsonObject)
        assertEquals(check, parsed)
        logger.trace { "my json: $jsonObject" }
    }

    @Test
    fun testWebsocket() {
        val dataHandler = EBDataHandler(TestSender(), sModule)
        val req = MySealedObject(Sealed1, SealedData(5), "Wow")
        val call = mockk<CalledTest>()
        every { call.call() } answers {}
        dataHandler.send(
            MySealedObject.serializer(),
            req,
            MySealedObject.serializer()
        ) {
            assertEquals(req, it)
            call.call()
        }
        verify { call.call() }
    }

    @Test
    fun testSerializeMap() {
        val dataHandler = EBDataHandler(TestSender())
        val req = MapHolder(
            5,
            mapOf(MapKey(5, "Wow") to MapValue("blbo", 10L), MapKey(6, "no") to MapValue("aua", 11L))
        )
        val call = mockk<CalledTest>()
        every { call.call() } answers {}
        dataHandler.send(MapHolder.serializer(), req, MapHolder.serializer()) {
            assertEquals(req, it)
            call.call()
        }
        verify { call.call() }
    }
}
