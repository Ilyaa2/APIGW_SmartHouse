package com.example.redis_transport.db_service

import com.example.entity.User
import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.lettuce.core.api.StatefulRedisConnection


@Serializable
data class ResponseData(val id: Int, val username: String, val password: String)

//PUBLISH responseChannel "{\"id\": 1, \"username\": \"myName\", \"password\": \"myPride\"}"
data class InsertMessage(val action: String, val table: String, val data: User)
data class SelectMessage(val action: String, val table: String, val conditions: Conditions)

data class Conditions(val username: String, val password: String)

val mapper = jacksonObjectMapper()

const val receiveChannel = "responseDBChannel"
const val sendChannel = "requestDBChannel"


object AuthDBService {
    private var redisClient: RedisClient = RedisClient.create("redis://localhost:6379")
    private var connection: StatefulRedisConnection<String, String> = redisClient.connect()

    fun registerUserInDB(userdata: User): ResponseData? {
        return runBlocking {
            sendAndGetUserCredsToDB(userdata)
        }
    }

    fun loginUserInDB(userdata: User): Boolean {
        return runBlocking {
            checkExistenceOfUserCredsToDB(userdata)
        }
    }

    private suspend fun sendAndGetUserCredsToDB(userdata: User): ResponseData? {
        val pubSubConnection = redisClient.connectPubSub()
        val pubSubCommands = pubSubConnection.sync()

        val insertMessage = InsertMessage("insert", "user", userdata)
        val insertMessageJson = mapper.writeValueAsString(insertMessage)


        val deferredResponse = CompletableDeferred<ResponseData?>()

        pubSubConnection.addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String, message: String) {
                if (channel == receiveChannel) {
                    val responseData = mapper.readValue(message, ResponseData::class.java)
                    deferredResponse.complete(responseData)
                }
            }
        })

        pubSubCommands.subscribe(receiveChannel)

        connection.async().publish(sendChannel, insertMessageJson)

        val response = withTimeoutOrNull(10000) { deferredResponse.await() }

        pubSubConnection.close()

        return response
    }

    private suspend fun checkExistenceOfUserCredsToDB(userdata: User): Boolean {
        val pubSubConnection = redisClient.connectPubSub()
        val pubSubCommands = pubSubConnection.sync()

        val insertMessage = SelectMessage("select", "user", Conditions(userdata.username, userdata.password))
        val insertMessageJson = mapper.writeValueAsString(insertMessage)

        val deferredResponse = CompletableDeferred<ResponseData?>()

        pubSubConnection.addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String, message: String) {
                if (channel == receiveChannel) {
                    val responseData = mapper.readValue(message, ResponseData::class.java)
                    deferredResponse.complete(responseData)
                }
            }
        })

        pubSubCommands.subscribe(receiveChannel)

        connection.async().publish(sendChannel, insertMessageJson)

        val response = withTimeoutOrNull(10000) { deferredResponse.await() }

        pubSubConnection.close()

        return response == null
    }
}