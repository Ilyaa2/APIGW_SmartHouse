package com.example.entity

import kotlinx.serialization.Serializable

@Serializable
data class User(val username: String, val password: String)
