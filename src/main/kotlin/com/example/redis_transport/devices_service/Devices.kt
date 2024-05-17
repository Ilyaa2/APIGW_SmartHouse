package com.example.redis_transport.devices_service

import com.example.entity.CreatedDevice
import com.example.redis_transport.RedisConnection
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

const val creationDevicesChannel = "create_controller"

fun createDevice(username: String, createdDevice: CreatedDevice) {
    val pubSubConnection = RedisConnection.client.connectPubSub()

    val message = "$username/${createdDevice.id}/${createdDevice.type}"
    RedisConnection.connection.async().publish(creationDevicesChannel, message)

    pubSubConnection.close()
}

fun getDeviceInfo(channelName: String, deviceId: Long): String? {
    val message = "get/$deviceId"
    return runBlocking {
        sendMsgToDeviceService(channelName, message)
    }
}

fun setDeviceValue(channelName: String, deviceId: Long, value: Int): String? {
    val message = "set/$deviceId/$value"
    return runBlocking {
        sendMsgToDeviceService(channelName, message)
    }
}

fun setModeOnDevice(channelName: String, deviceId: Long, mode: String): String? {
    val message = "$mode/$deviceId"
    return runBlocking {
        sendMsgToDeviceService(channelName, message)
    }
}

private suspend fun sendMsgToDeviceService(channelName: String, sendMessage: String): String? {
    val pubSubConnection = RedisConnection.client.connectPubSub()
    val pubSubCommands = pubSubConnection.sync()

    val deferredResponse = CompletableDeferred<String?>()
    val responseChannel = "${channelName}_response"

    pubSubConnection.addListener(object : RedisPubSubAdapter<String, String>() {
        override fun message(channel: String, message: String) {
            if (channel == responseChannel) {
                deferredResponse.complete(message)
            }
        }
    })

    pubSubCommands.subscribe(responseChannel)

    RedisConnection.connection.async().publish(channelName, sendMessage)

    val response = withTimeoutOrNull(10000) { deferredResponse.await() }

    pubSubConnection.close()

    return response
}
