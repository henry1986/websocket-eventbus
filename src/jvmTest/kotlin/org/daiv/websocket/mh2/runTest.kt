package org.daiv.websocket.mh2

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.websocket.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.cio.websocket.WebSockets
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import io.ktor.websocket.WebSockets
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration

actual fun runTest(block: suspend () -> Unit) = runBlocking { block() }

fun main() {
    val port = 5862
    val server = embeddedServer(Netty, port = port) {
        install(WebSockets) {
            pingPeriod = Duration.ofMinutes(1)
        }

        routing {
            webSocket("test") {
                DMHKtorWebsocketHandler(DMHWebsocketBuilder(KtorSender(this))) {
                    println("was canceled")
                }.listen()
                delay(10L* 1000L)
                println("hello3")
            }
        }
    }
    GlobalScope.launch {
        val client = HttpClient(CIO).config {
            install(io.ktor.client.features.websocket.WebSockets)
        }
        delay(1000L)
        client.webSocket("ws://localhost:$port/test") {
            println("hello1")
            DMHKtorWebsocketHandler(DMHWebsocketBuilder(KtorSender(this))){
                println("client was canceled")
            }.listen().join()
            println("hello2")
        }
    }
    server.start()

}