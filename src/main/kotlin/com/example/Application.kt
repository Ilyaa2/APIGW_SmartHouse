package com.example

import com.example.plugins.*
import com.example.redis_transport.RedisConnection
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json


fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

fun Application.module() {
    configureSerialization()
    configureRouting()

    launchRedisListener()
}

fun Application.launchRedisListener() {
    // Запуск Redis слушателя в фоновом режиме
    GlobalScope.launch {
        startRedisListener()
    }
}

const val alarmChannel = "alarmChannel"
const val mobileAppUrl = "http://localhost:/8081"
suspend fun startRedisListener() {
    val pubSubConnection = RedisConnection.client.connectPubSub()
    val pubSubCommands = pubSubConnection.sync()
    val client = HttpClient(CIO)

    pubSubConnection.addListener(object : RedisPubSubAdapter<String, String>() {
        override fun message(channel: String, message: String) {
            if (channel == alarmChannel) {
                println("Received message from Redis: $message")
                try {
                    runBlocking {
                        client.post(mobileAppUrl) {
                            contentType(ContentType.Application.Json)
                            setBody(message)
                        }
                    }
                } catch (e: Exception){
                    e.printStackTrace()
                }
            }
        }
    })
    pubSubCommands.subscribe(alarmChannel)
    //todo может тут нужно ставить какую-то блокировку или бесконечный цикл
}

/*
авторизация будет на сессиях. В случае успеха на ручке login тебе возвратится токен, который ты у себя сохранишь для
каждого пользователя и будешь вставлять его в заголовок Authorization у каждого запроса. Если я не получу этот токен или
он будет неправильный, то пользователя нужно редиректнуть на страницу с авторизацией.

У меня будет мапа { {имя пользователя, пароль} : токен }

post /registration  -  {username: "ilya", password: "someshit"} {} - регистрация
post /login     -      {username: "ilya", password: "someshit"} {} - логин
get /device     -    {}     {devices: [{id: 1, type: "teapot"}, {id: 1, type: "water_sensor"}]}  -  получить все девайсы
get /device/{id}  -   {}   {id: 1, type: "teapot", mode: "off"}  -     получить девайс по id (узнать тип устройства)   -    тут я должен
post /device    -   {type: "teapot"}   {id: 1, type: "teapot"}   -      создать девайс

post /device/settings/{id}   -   {value : 30}  {}
post /device/power/{id}      -   {mode: "off"}  {}
get /device/info/{id}        -   {}        {mode: "off", temperature: 30}
 */

