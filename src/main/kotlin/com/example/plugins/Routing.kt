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
import com.example.utils.isDeviceModeCorrect
import com.example.utils.parseResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.atomicfu.atomic
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object SessionStorage {
    val sessions = ConcurrentHashMap<String, User>()
    val requestCounter = atomic(0)
}

fun Application.configureRouting() {
    routing {
        post("/registration") {
            SessionStorage.requestCounter.incrementAndGet()
            val user: User = call.receive<User>()
            user.password = hashPassword(user.password)

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
            SessionStorage.requestCounter.incrementAndGet()
            val user = call.receive<User>()
            user.password = hashPassword(user.password)

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
            SessionStorage.requestCounter.incrementAndGet()
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@get
            }

            val user = SessionStorage.sessions[token]!!
            val devices = getAllDevicesFromDB(user)?.data

            if (devices == null) {
                call.respond(HttpStatusCode.InternalServerError, "db service doesn't response")
                return@get
            }

            call.respond(devices)
        }

        get("/device/{id}") {
            SessionStorage.requestCounter.incrementAndGet()
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@get
            }

            if (call.parameters["id"] == null) {
                call.respond(HttpStatusCode.BadRequest, "id of device missed in the query")
                return@get
            }

            val user = SessionStorage.sessions[token]!!

            val id = call.parameters["id"]!!.toLong()

            val createdDevice = getDeviceFromDB(user, id)?.data

            if (createdDevice == null) {
                call.respond(HttpStatusCode.InternalServerError, "db service doesn't response")
                return@get
            }

            call.respond(createdDevice)
        }

        post("/device") {
            SessionStorage.requestCounter.incrementAndGet()
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@post
            }

            val user = SessionStorage.sessions[token]!!
            val deviceType = call.receive<DeviceType>()

            val createdDevice = createDeviceInDB(user, deviceType.type)?.data

            if (createdDevice == null) {
                call.respond(HttpStatusCode.InternalServerError, "db service doesn't response")
                return@post
            }

            createDevice(user.username, createdDevice)
            call.respond(createdDevice)
        }

        post("/device/settings/{id}") {
            SessionStorage.requestCounter.incrementAndGet()
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@post
            }

            if (call.parameters["id"] == null) {
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
                call.respond(HttpStatusCode.OK, result.description)
            }
        }

        post("/device/power/{id}") {
            SessionStorage.requestCounter.incrementAndGet()
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@post
            }

            if (call.parameters["id"] == null) {
                call.respond(HttpStatusCode.InternalServerError, "device service doesn't response")
                return@post
            }

            val deviceMode: DeviceSettingMode = call.receive<DeviceSettingMode>()

            if (!isDeviceModeCorrect(deviceMode.mode)) {
                call.respond(HttpStatusCode.BadRequest, "incorrect device mode")
                return@post
            }

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
            SessionStorage.requestCounter.incrementAndGet()
            val token = call.request.header("Authorization")
            if (token == null || !SessionStorage.sessions.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@get
            }

            if (call.parameters["id"] == null) {
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
                call.respond(HttpStatusCode.OK, result.description)
            }
        }
    }
}
