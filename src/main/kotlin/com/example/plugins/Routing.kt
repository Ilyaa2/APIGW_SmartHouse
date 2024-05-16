package com.example.plugins

import com.example.entity.User
import com.example.transport.sendAndReceive
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object SessionStorage {
    val sessions = ConcurrentHashMap<String, User>()
}
fun Application.configureRouting() {
    routing {
        post("/registration") {
            val user = call.receive<User>()

            /*
            if (SessionStorage.users.containsKey(user.username)) {
                call.respond(HttpStatusCode.Conflict, "User already exists")
                return@post
            }
             */

            //todo отправить запрос через редис на бд сервис.
            //если все ок, то дальше. Если такой пользователь уже есть - плохой ответ.

            // Создаем токен сессии
            val token = UUID.randomUUID().toString()
            SessionStorage.sessions[token] = user

            // Отправляем токен пользователю
            call.respond(hashMapOf("token" to token))
        }

        post("/login") {
            val user = call.receive<User>()

            //todo отправить запрос через redis на бд.

            // Создаем токен сессии
            val token = UUID.randomUUID().toString()
            SessionStorage.sessions[token] = user

            // Отправляем токен пользователю
            call.respond(hashMapOf("token" to token))
        }

        get("test") {
            val a = sendAndReceive("myName", "myPride")
            call.respond(HttpStatusCode.OK, a?.username + a?.password)
        }

        get("/profile") {
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@get
            }

            val user = SessionStorage.sessions[token]!!
            call.respond(hashMapOf("username" to user.username))
        }

        // Пример другого защищенного маршрута
        get("/another-secure-endpoint") {
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@get
            }

            call.respond(HttpStatusCode.OK, "You have access to this secure endpoint!")
        }
    }
}
