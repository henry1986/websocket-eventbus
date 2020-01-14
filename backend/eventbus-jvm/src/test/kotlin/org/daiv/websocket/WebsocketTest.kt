package org.daiv.websocket

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import kotlin.test.assertEquals

class WebsocketTest :
    Spek({
        describe("test json") {
            class CalledTest {
                fun call() {

                }
            }

            val logger = KotlinLogging.logger {}
            on("test serialization") {
                //                it("enum test") {
//                    val check = defaultWaveChecker()
//                    val json = Json.stringify(WaveChecker.serializer(), check)
//                    val parsed = Json.parse(WaveChecker.serializer(), json)
//                    assertEquals(check, parsed)
//                    logger.trace { "my json: $json" }
//                }
                it("sealed test") {
                    val check = MySealedObject(Sealed1, SealedData(5), "Wow")
                    val json = Json(context = sModule)
//                                        val json = Json(JsonConfiguration.Default)
                    val jsonObject = json.stringify(MySealedObject.serializer(), check)
                    val parsed = json.parse(MySealedObject.serializer(), jsonObject)
                    assertEquals(check, parsed)
                    logger.trace { "my json: $jsonObject" }
                }
            }
            on("test websocket") {
                val dataHandler = EBDataHandler(TestSender(), sModule)
                val req = MySealedObject(Sealed1, SealedData(5), "Wow")
                val call = mockk<CalledTest>()
                every { call.call() } answers {}
                it("test send") {
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
            }
            on("test serialization of map") {
                val dataHandler = EBDataHandler(TestSender())
                val req = MapHolder(
                    5,
                    mapOf(MapKey(5, "Wow") to MapValue("blbo", 10L), MapKey(6, "no") to MapValue("aua", 11L))
                )
                it("test Map with complex key serialization") {
                    val call = mockk<CalledTest>()
                    every { call.call() } answers {}
                    dataHandler.send(MapHolder.serializer(), req, MapHolder.serializer()) {
                        assertEquals(req, it)
                        call.call()
                    }
                    verify { call.call() }
                }
            }
        }
    })