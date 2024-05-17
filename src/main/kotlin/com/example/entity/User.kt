package com.example.entity

import kotlinx.serialization.Serializable

@Serializable
data class User(var username: String, var password: String)

@Serializable
data class CreatedUser(val id: Int, val username: String, val password: String)