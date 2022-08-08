package org.daiv.socket

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import org.daiv.websocket.mh2.parse
import org.daiv.websocket.mh2.stringify
import java.net.ServerSocket
import java.net.Socket

interface ExtendedSocket {
    val socket: Socket
    fun <T : Any> send(serializer: KSerializer<T>, t: T) {
        val string = serializer.stringify(t)
        val buffered = socket.getOutputStream().bufferedWriter()
        buffered.write(string)
        buffered.newLine()
        buffered.flush()
    }

    fun <T : Any> receive(serializer: KSerializer<T>): T {
        return serializer.parse(EmptySerializersModule, socket.getInputStream().bufferedReader().readLine())
    }

    fun close() {
        socket.close()
    }
}

class SocketEndpoint(override val socket: Socket) : ExtendedSocket {

    companion object {
        fun startClient(connectionDataAsString: String): SocketEndpoint {
            val connectionData = ConnectionData.serializer().parse(EmptySerializersModule, connectionDataAsString)
            return startClientSocket(connectionData)
        }

        fun startClientSocket(connectionData: ConnectionData): SocketEndpoint {
            return SocketEndpoint(Socket(connectionData.ip, connectionData.port))
        }

        fun startServerSocket(port: Int): SocketEndpoint {
            val socket = ServerSocket(port)
            val got = socket.accept()
            return SocketEndpoint(got)
        }

        fun requestFromFile(jarFile: String): SocketEndpoint {
            val port = 55359
            val p = ProcessBuilder(
                "java",
                "-jar",
                jarFile,
                ConnectionData.serializer().stringify(ConnectionData("localhost", port))
            )
            GlobalScope.launch {
                p.inheritIO()
                delay(100L)
                val run = p.start()
            }
            return startServerSocket(port)
        }
    }
}

