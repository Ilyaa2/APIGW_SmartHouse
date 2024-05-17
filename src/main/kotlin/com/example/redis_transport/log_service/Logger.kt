package com.example.redis_transport.log_service

import com.example.entity.CreatedDevice
import com.example.redis_transport.RedisConnection
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val loggerChannel = "log"

fun sendLog(event: String) {
    val pubSubConnection = RedisConnection.client.connectPubSub()
    val currentTime = LocalTime.now()
    val formattedTime = currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

    val message = "api-gw/$event/$formattedTime"
    RedisConnection.connection.async().publish(loggerChannel, message)

    pubSubConnection.close()
}