package org.daiv.websocket.mh2

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration

actual fun runTest(block: suspend () -> Unit) = runBlocking { block() }

fun main() {
    val port = 5862
    // ...
    val server = embeddedServer(Netty, port = port) {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(15)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        routing {
            webSocket("test") {
                val handler = KtorWebsocketHandler(WebsocketBuilder(KtorSender(this), DMHMessageFactory)) {
                    println("was canceled")
                }
                handler.listen()
                delay(10L * 1000L)
                println("hello3")
            }
        }
    }
    GlobalScope.launch {
        val client = HttpClient(CIO).config {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }
        delay(1000L)
        client.webSocket("ws://localhost:$port/test") {
            println("hello1")
            KtorWebsocketHandler(WebsocketBuilder(KtorSender(this), DMHMessageFactory)) {
                println("client was canceled")
            }.listen()
            println("hello2")
        }
    }
    server.start()
    println("blubs")
}