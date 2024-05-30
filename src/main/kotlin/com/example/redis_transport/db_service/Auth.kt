package com.example.redis_transport.db_service

import com.example.entity.CreatedUser
import com.example.entity.User
import com.example.redis_transport.RedisConnection
import com.example.redis_transport.mapper
import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.lettuce.core.api.StatefulRedisConnection

//PUBLISH responseDBChannel "{\"req\":2, \"id\": 1, \"username\": \"myName\", \"password\": \"myPride\"}"
//PUBLISH responseDBChannel "{\"req\":1, \"id\": 1, \"username\": \"myName\", \"password\": \"myPride\"}"



//PUBLISH responseDBChannel "{\"req\": 1, \"data\":{ \"id\": 1, \"type\": \"teapot\"}}"
//PUBLISH responseDBChannel "{\"req\": 1, \"data\":{ \"id\": 2, \"type\": \"water_sensor\"}}"


//PUBLISH responseDBChannel "{ \"req\": 1, \"data\":[{\"id\": 1, \"type\": \"teapot\"}, {\"id\": 2, \"type\": \"water_sensor\"}]}"
//PUBLISH myName {value : 30}
data class InsertUserMessage(val req: Long, val action: String, val table: String, val data: User)
data class SelectUserMessage(val req: Long, val action: String, val table: String, val conditions: Conditions)

data class Conditions(val username: String, val password: String)

const val receiveChannel = "responseDBChannel"
const val sendChannel = "requestDBChannel"

object AuthDBService {
    //todo по идее с базой данных такие посылки через redis работать не будет, потому что один канал и в него идет большое кол-во ненужных сообщений от других клиентов
    //сообщения других клиентов просто будут путаться между собой. Скорее всего нужно присвоить id сообщению перед посылкой на бд сервис и
    //в лисенере ожидать что придет точно такой же id и только тогда выходить из него и закрывать соединение.

    fun registerUserInDB(userdata: User): CreatedUser? {
        val randomId = (0..Long.MAX_VALUE).random()
        val insertUserMessage = InsertUserMessage(randomId, "insert", "user", userdata)

        return runBlocking {
            sendAndGetUserCredsToDB(insertUserMessage)
        }
    }

    fun loginUserInDB(userdata: User): Boolean {
        val randomId = (0..Long.MAX_VALUE).random()
        val selectUserMessage =
            SelectUserMessage(randomId, "select", "user", Conditions(userdata.username, userdata.password))

        return runBlocking {
            sendAndGetUserCredsToDB(selectUserMessage) == null
        }
    }

    private suspend fun sendAndGetUserCredsToDB(message: Any): CreatedUser? {
        val pubSubConnection = RedisConnection.client.connectPubSub()
        val pubSubCommands = pubSubConnection.sync()

        val tmp1 = message as? SelectUserMessage
        val tmp2 = message as? InsertUserMessage
        var req: Long = 0
        if (tmp1 == null) {
            if (tmp2 != null) {
                req = tmp2.req
            }
        } else {
            req = tmp1.req
        }

        val insertMessageJson = mapper.writeValueAsString(message)
        val deferredResponse = CompletableDeferred<CreatedUser?>()

        pubSubConnection.addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String, message: String) {
                if (channel == receiveChannel) {
                    val createdUser = mapper.readValue(message, CreatedUser::class.java)
                    if (createdUser.req == req) {
                        deferredResponse.complete(createdUser)
                    }
                }
            }
        })

        pubSubCommands.subscribe(receiveChannel)

        RedisConnection.connection.async().publish(sendChannel, insertMessageJson)

        val response = withTimeoutOrNull(10000) { deferredResponse.await() }

        pubSubConnection.close()

        return response
    }
}