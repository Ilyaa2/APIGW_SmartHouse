package com.example.redis_transport.db_service

import com.example.entity.User
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

object DevicesDBService {
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

    //todo добавить метод на добавление устройства в бд

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