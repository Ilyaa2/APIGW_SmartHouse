package com.example.plugins

import com.example.entity.User
import com.example.redis_transport.db_service.AuthDBService.loginUserInDB
import com.example.redis_transport.db_service.AuthDBService.registerUserInDB
import com.example.utils.hashPassword
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object SessionStorage {
    val sessions = ConcurrentHashMap<String, User>()
}

fun Application.configureRouting() {
    routing {
        post("/registration") {
            val user: User = call.receive<User>()
            user.username = hashPassword(user.username)

            val createdUser = registerUserInDB(User(user.username, user.password))
            if (createdUser == null) {
                call.respond(HttpStatusCode.InternalServerError, "db service doesn't response")
                return@post
            }

            val token = UUID.randomUUID().toString()
            SessionStorage.sessions[token] = user

            call.respond(hashMapOf("token" to token))
        }

        post("/login") {
            val user = call.receive<User>()
            user.username = hashPassword(user.username)

            val isValid = loginUserInDB(user)

            if (!isValid) {
                call.respond(HttpStatusCode.Unauthorized, "Incorrect password or db service doesn't response")
                return@post
            }

            val token = UUID.randomUUID().toString()
            SessionStorage.sessions[token] = user

            call.respond(hashMapOf("token" to token))
        }

        get("/device") {
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@get
            }


        }

    }
}
