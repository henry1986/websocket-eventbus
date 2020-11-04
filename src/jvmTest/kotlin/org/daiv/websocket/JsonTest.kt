package org.daiv.websocket

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.serialization.Serializable
import kotlin.test.Test

@Serializable
data class TestData2(val x: String)

class JsonTest {
    data class TestData(val x:String, val map: Map<Int,Double>)


    @Test
    fun testJson(){
        val gson = GsonBuilder().setPrettyPrinting().create()
        val x = gson.toJson(TestData("hel", mapOf(5 to 6.0, 6 to 7.0)))
        println(x)
        val y = gson.fromJson(x, TestData::class.java)
        println(y)
        val d = (1..4).map { it to 0.8 }.toMap()
        println("d: $d")
    }


    @Test
    fun testSerializer(){
        val s = TestData2.serializer()
        println("${s.descriptor.serialName}")

    }
}