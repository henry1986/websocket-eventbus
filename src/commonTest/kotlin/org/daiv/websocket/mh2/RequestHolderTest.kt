package org.daiv.websocket.mh2

import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlin.test.*

class RequestHolderTest {

    @Serializable
    data class Senddata(val x: Int)

    @Serializable
    data class ResponseSent(val x: Int)

    fun <T : Any> EBMessageHeader2.answer(answer: T, serializer: KSerializer<T>, isAnswer: Boolean): EBMessageHeader2 {
        val answerString = serializer.stringify(answer)
        return EBMessageHeader2(header, serializer.descriptor.serialName, null, isAnswer, responseId, answerString)
    }

    class TestHandler(val isToHandle: Boolean) : RequestHolderHandler<EBMessageHeader2> {
        var wasCalled: MessageData? = null
        override fun handle(ebMessageHeader: EBMessageHeader2): Boolean {
            return isToHandle
        }

        override suspend fun doHandle(ebMessageHeader: EBMessageHeader2, context: SerializersModule) {
            wasCalled = ebMessageHeader
        }
    }

    @Test
    fun test() = runTest {
        val resChannel = Channel<SendSerializable>()
        val expect = ResponseSent(9)
        val handlers = listOf(TestHandler(false), TestHandler(true))
        val holder = RequestHolder(EmptySerializersModule, object : WSSendable {
            override suspend fun send(messageHeader: SendSerializable) {
                resChannel.send(messageHeader)
            }
        }, ResponseStore(), handlers)
        holder.send(Senddata.serializer(), Senddata(6), ResponseSent.serializer()) {
        }
        val s = resChannel.receive() as EBMessageHeader2
        val res = s.answer(expect, ResponseSent.serializer(), true)
        holder.onMessage(res)
        assertNull(handlers[0].wasCalled)
        assertEquals(handlers[1].wasCalled, res)
    }


    val answerable = DefaultWSAnswerable(listOf(object : SimpleRequestResponse<Senddata, ResponseSent> {
        override val serializer: KSerializer<Senddata> = Senddata.serializer()
        override val response: KSerializer<ResponseSent> = ResponseSent.serializer()

        override suspend fun onMessage(request: Senddata): ResponseSent {
            return ResponseSent(request.x + 1)
        }
    }), object : WSSendable {
        override suspend fun send(messageHeader: SendSerializable) {

        }
    }, WSErrorLogger { })

    val storable = DefaultResponseStorable(ResponseStore())

    @Test
    fun wsResponsableTest() = runTest {
        val serializer = Senddata.serializer()
        val toSent = Senddata(5)
        var sent: Senddata? = null
        val wsResponseable = DefaultWSResponsable(listOf(SimpleWSResponder(Senddata.serializer()) {
            sent = it
        }))
        val m = EBMessageHeader2(
            "mh2", serializer.descriptor.serialName, null, false, null, serializer.stringify(toSent)
        )
        assertFalse(answerable.handle(m), "shouldn't be an answerable")
        assertFalse(storable.handle(m), "shouldn't be handled by storable")
        assertTrue(wsResponseable.handle(m), "should be handled by wsResponseable")
        wsResponseable.doHandle(m, EmptySerializersModule)
        assertEquals(toSent, sent)
    }

    @Test
    fun wsResponsableErrorTest() = runTest {
        val serializer = Senddata.serializer()
        val toSent = Senddata(5)
        var sent: Senddata? = null
        val wsResponseable = DefaultWSResponsable(listOf(SimpleWSResponder(Senddata.serializer()) {
            throw RuntimeException("test exception")
        }))
        val m = EBMessageHeader2(
            "mh2", serializer.descriptor.serialName, null, false, null, serializer.stringify(toSent)
        )
        assertFalse(answerable.handle(m), "shouldn't be an answerable")
        assertFalse(storable.handle(m), "shouldn't be handled by storable")
        assertTrue(wsResponseable.handle(m), "should be handled by wsResponseable")
        try {
            wsResponseable.doHandle(m, EmptySerializersModule)
            fail("exception should have been thrown")
        } catch (w: WSResponseAble.WSResponseException){

        }
    }

    val wsResponseable = DefaultWSResponsable(listOf(SimpleWSResponder(Senddata.serializer()) {
    }))

    @Test
    fun responseStorableTest() = runTest {
        val serializer = ResponseSent.serializer()
        var responseSent: EBMessageHeader2? = null
        val storable = DefaultResponseStorable(object : IdGetter<EBMessageHeader2> {
            override suspend fun removeId(responseId: String): ResponseStore.WSResponse<EBMessageHeader2>? {
                return if (responseId == "testId-1") ResponseStore.WSResponse( {}, {
                    responseSent = it
                }) else null
            }
        })
        val toSent = ResponseSent(5)
        val m = EBMessageHeader2(
            "mh2", serializer.descriptor.serialName, null, true, "testId-1", serializer.stringify(toSent)
        )
        assertFalse(answerable.handle(m), "shouldn't be an answerable")
        assertTrue(storable.handle(m), "should be handled by storable")
        assertFalse(wsResponseable.handle(m), "shouldn't be an wsResponseable")
        storable.doHandle(m, EmptySerializersModule)
        assertEquals(m, responseSent)
    }

    @Test
    fun responseStorableExceptionTest() = runTest {
        val serializer = ResponseSent.serializer()
        var responseSent: EBMessageHeader2? = null
        val storable = DefaultResponseStorable(object : IdGetter<EBMessageHeader2> {
            override suspend fun removeId(responseId: String): ResponseStore.WSResponse<EBMessageHeader2>? {
                return if (responseId == "testId-1") ResponseStore.WSResponse( {}, {
                    responseSent = it
                }) else null
            }
        })
        val toSent = ResponseSent(5)
        val m = EBMessageHeader2(
            "EMH2", serializer.descriptor.serialName, null, true, "testId-2", serializer.stringify(toSent)
        )
        assertFalse(answerable.handle(m), "shouldn't be an answerable")
        assertTrue(storable.handle(m), "should be handled by storable")
        assertFalse(wsResponseable.handle(m), "shouldn't be an wsResponseable")
        try {
            storable.doHandle(m, EmptySerializersModule)
            fail("exception should have been thrown")
        } catch (t:ResponseStorable.ResponseStoreExecption){

        }
    }

    @Test
    fun answerableTest() = runTest {
        val serializer = ResponseSent.serializer()
        var responseSent: ResponseSent? = null
        var sentEBH:SendSerializable? = null
        val sender = object : WSSendable {
            override suspend fun send(messageHeader: SendSerializable) {
                sentEBH = messageHeader
            }
        }
        val answerable = DefaultWSAnswerable(listOf(object : SimpleRequestResponse<Senddata, ResponseSent> {
            override val serializer: KSerializer<Senddata> = Senddata.serializer()
            override val response: KSerializer<ResponseSent> = ResponseSent.serializer()

            override suspend fun onMessage(request: Senddata): ResponseSent {
                return ResponseSent(request.x + 1)
            }
        }), sender, WSErrorLogger { })
        val sendData = Senddata(9)
        val start = EBMessageHeader2(
            "mh2",
            Senddata.serializer().descriptor.serialName,
            null,
            false,
            "responseId-1",
            Senddata.serializer().stringify(sendData)
        )
//        sender.send(start)
        val toSent = ResponseSent(10)
        assertTrue(answerable.handle(start), "should be an answerable")
        assertFalse(storable.handle(start), "shouldn't be handled by storable")
        assertFalse(wsResponseable.handle(start), "shouldn't be an wsResponseable")
        answerable.doHandle(start, EmptySerializersModule)
        val m = EBMessageHeader2(
            "EMH2", serializer.descriptor.serialName, null, true, "responseId-1", serializer.stringify(toSent)
        )
        assertEquals(m, sentEBH)
    }
}
