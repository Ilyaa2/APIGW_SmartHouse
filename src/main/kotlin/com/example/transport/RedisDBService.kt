package com.example.transport

import com.example.entity.User
import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

@Serializable
data class PubSubMessage(val action: String, val table: String, val data: User)

@Serializable
data class ResponseMessage(val data: ResponseData)

@Serializable
data class ResponseData(val id: Int, val username: String, val password: String)

//PUBLISH responseChannel "{\"id\": 1, \"username\": \"myName\", \"password\": \"myPride\"}"

/*
fun sendAndReceive(username: String, password: String): ResponseMessage? {
    val redisClient = RedisClient.create("redis://localhost:6379")
    val connection = redisClient.connectPubSub()
    val pubSubCommands: RedisPubSubReactiveCommands<String, String> = connection.reactive()

    val user = User(username, password)
    val message = PubSubMessage(action = "insert", table = "user", data = user)
    val jsonMessage = Json.encodeToString(message)

    val requestChannel = "request_channel"
    val responseChannel = "response_channel"

    val futureResponse = CompletableFuture<ResponseMessage>()

    // Подписка на канал для получения ответа
    val subscription = pubSubCommands.subscribe(responseChannel)
        .thenMany(pubSubCommands.observeChannels(responseChannel))
        .filter { msg -> msg != null }
        .map { msg -> Json.decodeFromString<ResponseMessage>(msg) }
        .doOnNext { responseMessage -> futureResponse.complete(responseMessage) }
        .subscribe()

    // Публикация сообщения в канал
    pubSubCommands.publish(requestChannel, jsonMessage).subscribe()

    return try {
        futureResponse.get(10, TimeUnit.SECONDS)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } finally {
        subscription.dispose()
        connection.close()
        redisClient.shutdown()
    }
}
 */


data class UserData(val username: String, val password: String)
data class InsertMessage(val action: String, val table: String, val data: UserData)

val mapper = jacksonObjectMapper()

suspend fun sendAndReceive(username: String, password: String): ResponseData? {
    val redisClient = RedisClient.create("redis://localhost:6379")
    val connection = redisClient.connect()
    val pubSubConnection = redisClient.connectPubSub()
    val pubSubCommands = pubSubConnection.sync()

    val insertMessage = InsertMessage("insert", "user", UserData(username, password))
    val insertMessageJson = mapper.writeValueAsString(insertMessage)

    val receiveChannel = "responseChannel"
    val sendChannel = "requestChannel"

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
    connection.close()
    redisClient.shutdown()

    return response
}