package com.example.redis_transport.db_service

import com.example.entity.*
import com.example.redis_transport.RedisConnection
import com.example.redis_transport.mapper
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

data class InsertDeviceMessage(val action: String, val table: String, val data: DeviceRequestData)

data class SelectDeviceMessage(val action: String, val table: String, val conditions: DeviceCondition)

fun getAllDevicesFromDB(userdata: User): Array<CreatedDevice>? {
    return runBlocking {
        getDevicesFromDB(userdata)
    }
}

fun getDeviceFromDB(userdata: User, id: Long): CreatedDevice? {
    val selectMessage =
        SelectDeviceMessage("select", "userDevices", DeviceCondition(userdata.username, userdata.password, id))
    return runBlocking {
        getOrCreateDeviceInDB(selectMessage)
    }
}

fun createDeviceInDB(userdata: User, deviceType: String): CreatedDevice? {
    val data = DeviceRequestData(userdata.username, userdata.password, deviceType)
    val insertMessage = InsertDeviceMessage("insert", "userDevices", data)
    return runBlocking {
        getOrCreateDeviceInDB(insertMessage)
    }
}

private suspend fun getDevicesFromDB(userData: User): Array<CreatedDevice>? {
    val pubSubConnection = RedisConnection.client.connectPubSub()
    val pubSubCommands = pubSubConnection.sync()

    val selectUserMessage =
        SelectUserMessage("select", "userDevices", Conditions(userData.username, userData.password))

    val selectMessageJson = mapper.writeValueAsString(selectUserMessage)
    val deferredResponse = CompletableDeferred<Array<CreatedDevice>>()

    pubSubConnection.addListener(object : RedisPubSubAdapter<String, String>() {
        override fun message(channel: String, message: String) {
            if (channel == receiveChannel) {
                //MutableList
                val devices = mapper.readValue(message, Array<CreatedDevice>::class.java)
                deferredResponse.complete(devices)
            }
        }
    })

    pubSubCommands.subscribe(receiveChannel)

    RedisConnection.connection.async().publish(sendChannel, selectMessageJson)

    val response = withTimeoutOrNull(10000) { deferredResponse.await() }

    pubSubConnection.close()

    return response
}

private suspend fun getOrCreateDeviceInDB(message: Any): CreatedDevice? {
    val pubSubConnection = RedisConnection.client.connectPubSub()
    val pubSubCommands = pubSubConnection.sync()

    val messageJson = mapper.writeValueAsString(message)

    val deferredResponse = CompletableDeferred<CreatedDevice?>()

    pubSubConnection.addListener(object : RedisPubSubAdapter<String, String>() {
        override fun message(channel: String, message: String) {
            if (channel == receiveChannel) {
                val createdDevice = mapper.readValue(message, CreatedDevice::class.java)
                deferredResponse.complete(createdDevice)
            }
        }
    })

    pubSubCommands.subscribe(receiveChannel)

    RedisConnection.connection.async().publish(sendChannel, messageJson)

    val response = withTimeoutOrNull(10000) { deferredResponse.await() }

    pubSubConnection.close()

    return response
}