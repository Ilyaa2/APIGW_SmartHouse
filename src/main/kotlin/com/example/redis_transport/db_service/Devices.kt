package com.example.redis_transport.db_service

import com.example.entity.*
import com.example.redis_transport.RedisConnection
import com.example.redis_transport.mapper
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

data class InsertDeviceMessage(val req: Long, val action: String, val table: String, val data: DeviceRequestData)

data class SelectDeviceMessage(val req: Long, val action: String, val table: String, val conditions: DeviceCondition)

fun getAllDevicesFromDB(userdata: User): AllDevicesResponse? {
    return runBlocking {
        getDevicesFromDB(userdata)
    }
}

fun getDeviceFromDB(userdata: User, id: Long): CreatedDeviceResponse? {
    val randomId = (0..Long.MAX_VALUE).random()
    val selectMessage =
        SelectDeviceMessage(
            randomId,
            "select",
            "userDevices",
            DeviceCondition(userdata.username, userdata.password, id)
        )
    return runBlocking {
        getOrCreateDeviceInDB(selectMessage)
    }
}

fun createDeviceInDB(userdata: User, deviceType: String): CreatedDeviceResponse? {
    val randomId = (0..Long.MAX_VALUE).random()
    val data = DeviceRequestData(userdata.username, userdata.password, deviceType)
    val insertMessage = InsertDeviceMessage(randomId, "insert", "userDevices", data)
    return runBlocking {
        getOrCreateDeviceInDB(insertMessage)
    }
}

private suspend fun getDevicesFromDB(userData: User): AllDevicesResponse? {
    val pubSubConnection = RedisConnection.client.connectPubSub()
    val pubSubCommands = pubSubConnection.sync()
    val randomId = (0..Long.MAX_VALUE).random()
    val selectUserMessage =
        SelectUserMessage(randomId, "select", "userDevices", Conditions(userData.username, userData.password))

    val selectMessageJson = mapper.writeValueAsString(selectUserMessage)
    val deferredResponse = CompletableDeferred<AllDevicesResponse>()

    pubSubConnection.addListener(object : RedisPubSubAdapter<String, String>() {
        override fun message(channel: String, message: String) {
            if (channel == receiveChannel) {
                //MutableList
                val devices = mapper.readValue(message, AllDevicesResponse::class.java)
                if (selectUserMessage.req == devices.req) {
                    deferredResponse.complete(devices)
                }
            }
        }
    })

    pubSubCommands.subscribe(receiveChannel)

    RedisConnection.connection.async().publish(sendChannel, selectMessageJson)

    val response = withTimeoutOrNull(10000) { deferredResponse.await() }

    pubSubConnection.close()

    return response
}

private suspend fun getOrCreateDeviceInDB(message: Any): CreatedDeviceResponse? {
    val pubSubConnection = RedisConnection.client.connectPubSub()
    val pubSubCommands = pubSubConnection.sync()

    val messageJson = mapper.writeValueAsString(message)

    val tmp1 = message as? SelectDeviceMessage
    val tmp2 = message as? InsertDeviceMessage
    var req: Long = 0
    if (tmp1 == null) {
        if (tmp2 != null) {
            req = tmp2.req
        }
    } else {
        req = tmp1.req
    }


    val deferredResponse = CompletableDeferred<CreatedDeviceResponse?>()

    pubSubConnection.addListener(object : RedisPubSubAdapter<String, String>() {
        override fun message(channel: String, message: String) {
            if (channel == receiveChannel) {
                val createdDevice = mapper.readValue(message, CreatedDeviceResponse::class.java)
                if (req == createdDevice.req) {
                    deferredResponse.complete(createdDevice)
                }
            }
        }
    })

    pubSubCommands.subscribe(receiveChannel)

    RedisConnection.connection.async().publish(sendChannel, messageJson)

    val response = withTimeoutOrNull(10000) { deferredResponse.await() }

    pubSubConnection.close()

    return response
}