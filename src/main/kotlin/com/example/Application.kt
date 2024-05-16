package com.example

import com.example.plugins.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureSockets()
    configureSecurity()
    configureRouting()
}
/*
авторизация будет на сессиях. В случае успеха на ручке login тебе возвратится токен, который ты у себя сохранишь для
каждого пользователя и будешь вставлять его в заголовок Authorization у каждого запроса. Если я не получу этот токен или
он будет неправильный, то пользователя нужно редиректнуть на страницу с авторизацией.

У меня будет мапа { {имя пользователя, пароль} : токен }

post /registration  -  {username: "ilya", password: "someshit"} {} - регистрация
post /login     -      {username: "ilya", password: "someshit"} {} - логин
get /device     -    {}     {devices: [{id: 1, type: "teapot"}, {id: 1, type: "water_sensor"}]}  -  получить все девайсы
get /device/{id}  -   {}   {id: 1, type: "teapot"}  -     получить девайс по id (узнать тип устройства)
post /device    -   {type: "teapot"}   {id: 1, type: "teapot"}   -      создать девайс
post /device/settings/{id}   -   {value : 30}  {}
post /device/power/{id}      -   {mode: "off"}  {}
get /device/info/{id}        -   {}        {все что угодно}
 */

