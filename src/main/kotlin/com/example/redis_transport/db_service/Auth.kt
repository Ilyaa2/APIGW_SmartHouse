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

//PUBLISH responseChannel "{\"id\": 1, \"username\": \"myName\", \"password\": \"myPride\"}"
data class InsertUserMessage(val action: String, val table: String, val data: User)
data class SelectUserMessage(val action: String, val table: String, val conditions: Conditions)

data class Conditions(val username: String, val password: String)

const val receiveChannel = "responseDBChannel"
const val sendChannel = "requestDBChannel"

object AuthDBService {
    //todo по идее с базой данных такие посылки через redis работать не будет, потому что один канал и в него идет большое кол-во ненужных сообщений от других клиентов
    //сообщения других клиентов просто будут путаться между собой. Скорее всего нужно присвоить id сообщению перед посылкой на бд сервис и
    //в лисенере ожидать что придет точно такой же id и только тогда выходить из него и закрывать соединение.

    fun registerUserInDB(userdata: User): CreatedUser? {
        val insertUserMessage = InsertUserMessage("insert", "user", userdata)

        return runBlocking {
            sendAndGetUserCredsToDB(insertUserMessage)
        }
    }

    fun loginUserInDB(userdata: User): Boolean {
        val selectUserMessage = SelectUserMessage("select", "user", Conditions(userdata.username, userdata.password))

        return runBlocking {
            sendAndGetUserCredsToDB(selectUserMessage) == null
        }
    }

    private suspend fun sendAndGetUserCredsToDB(message: Any): CreatedUser? {
        val pubSubConnection = RedisConnection.client.connectPubSub()
        val pubSubCommands = pubSubConnection.sync()

        val insertMessageJson = mapper.writeValueAsString(message)

        val deferredResponse = CompletableDeferred<CreatedUser?>()

        pubSubConnection.addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String, message: String) {
                if (channel == receiveChannel) {
                    val createdUser = mapper.readValue(message, CreatedUser::class.java)
                    deferredResponse.complete(createdUser)
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