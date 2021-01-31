package org.daiv.websocket.mh2

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.promise
import kotlin.test.Test

actual fun runTest(block: suspend () -> Unit): dynamic = GlobalScope.promise { block() }

suspend fun toTest() {
    println("hello from test")
}

class TestJS{
    @Test
    fun test() {
        runTest {
            toTest()
        }
    }
}