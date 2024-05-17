package com.example.plugins

import com.example.entity.DeviceSettingMode
import com.example.entity.DeviceSettingValue
import com.example.entity.DeviceType
import com.example.entity.User
import com.example.redis_transport.db_service.AuthDBService.loginUserInDB
import com.example.redis_transport.db_service.AuthDBService.registerUserInDB
import com.example.redis_transport.db_service.createDeviceInDB
import com.example.redis_transport.db_service.getAllDevicesFromDB
import com.example.redis_transport.db_service.getDeviceFromDB
import com.example.redis_transport.devices_service.createDevice
import com.example.redis_transport.devices_service.getDeviceInfo
import com.example.redis_transport.devices_service.setDeviceValue
import com.example.redis_transport.devices_service.setModeOnDevice
import com.example.utils.hashPassword
import com.example.utils.parseResponse
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
    //todo сделать прослушивание канала alarm в который посылаются сообщений и отправка ире
    //alarm    -       device_type/event

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

            val user = SessionStorage.sessions[token]!!
            val devices = getAllDevicesFromDB(user)

            if (devices == null) {
                call.respond(HttpStatusCode.InternalServerError, "db service doesn't response")
                return@get
            }

            call.respond(devices)
        }

        get("/device/{id}") {
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@get
            }

            if (call.parameters["id"] != null) {
                call.respond(HttpStatusCode.BadRequest, "id of device missed in the query")
                return@get
            }

            val user = SessionStorage.sessions[token]!!
            val id = call.parameters["id"]!!.toLong()

            val createdDevice = getDeviceFromDB(user, id)

            if (createdDevice == null) {
                call.respond(HttpStatusCode.InternalServerError, "db service doesn't response")
                return@get
            }

            call.respond(createdDevice)
        }

        post("/device") {
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@post
            }

            val user = SessionStorage.sessions[token]!!
            val deviceType = call.receive<DeviceType>()

            val createdDevice = createDeviceInDB(user, deviceType.type)

            if (createdDevice == null) {
                call.respond(HttpStatusCode.InternalServerError, "db service doesn't response")
                return@post
            }
            createDevice(user.username, createdDevice)
            call.respond(createdDevice)
        }

        post("/device/settings/{id}") {
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@post
            }

            if (call.parameters["id"] != null) {
                call.respond(HttpStatusCode.InternalServerError, "device service doesn't response")
                return@post
            }

            val settingValue: DeviceSettingValue = call.receive<DeviceSettingValue>()
            val user = SessionStorage.sessions[token]!!
            val id = call.parameters["id"]!!.toLong()
            val response = setDeviceValue(user.username, id, settingValue.value)

            if (response == null) {
                call.respond(HttpStatusCode.InternalServerError, "device service doesn't response")
                return@post
            }

            val result = parseResponse(response)
            if (result.wasError){
                call.respond(HttpStatusCode.BadRequest, result.description)
            } else {
                //todo может нужно посылать текст. Через call.respondText
                call.respond(HttpStatusCode.OK, result.description)
            }
        }

        post("/device/power/{id}") {
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@post
            }

            if (call.parameters["id"] != null) {
                call.respond(HttpStatusCode.InternalServerError, "device service doesn't response")
                return@post
            }

            val deviceMode: DeviceSettingMode = call.receive<DeviceSettingMode>()
            val user = SessionStorage.sessions[token]!!
            val id = call.parameters["id"]!!.toLong()
            val response = setModeOnDevice(user.username, id, deviceMode.mode)

            if (response == null) {
                call.respond(HttpStatusCode.InternalServerError, "device service doesn't response")
                return@post
            }

            val result = parseResponse(response)
            if (result.wasError){
                call.respond(HttpStatusCode.BadRequest, result.description)
            } else {
                call.respond(HttpStatusCode.OK)
            }
        }

        get("/device/info/{id}") {
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@get
            }

            if (call.parameters["id"] != null) {
                call.respond(HttpStatusCode.InternalServerError, "device service doesn't response")
                return@get
            }

            val user = SessionStorage.sessions[token]!!
            val id = call.parameters["id"]!!.toLong()
            val response = getDeviceInfo(user.username, id)

            if (response == null) {
                call.respond(HttpStatusCode.InternalServerError, "device service doesn't response")
                return@get
            }

            val result = parseResponse(response)
            if (result.wasError){
                call.respond(HttpStatusCode.BadRequest, result.description)
            } else {
                //todo может нужно посылать текст. Через call.respondText
                call.respond(HttpStatusCode.OK, result.description)
            }
        }
    }
}
