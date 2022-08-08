package org.daiv.socket

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.daiv.websocket.mh2.runTest
import org.junit.Test
import kotlin.test.assertEquals

class SocketEndpointTest {

    @Serializable
    data class ClientData(val x: Int)

    @Serializable
    data class ResponseData(val y: Double)

    @Test
    fun test() = runTest {
        val job = GlobalScope.launch {
            val server = SocketEndpoint.startServerSocket(5006)
            server.send(ClientData.serializer(), ClientData(5))
            val response = server.receive(ResponseData.serializer())
            assertEquals(ResponseData(6.8), response)
            println("got correct data, shutting down")
            server.close()
        }
        val client = SocketEndpoint.startClientSocket(ConnectionData("localhost", 5006))
        val clientData = client.receive(ClientData.serializer())
        assertEquals(ClientData(5), clientData)
        client.send(ResponseData.serializer(), ResponseData(6.8))
        client.close()
        job.join()
    }
}