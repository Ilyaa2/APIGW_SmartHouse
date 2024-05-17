package com.example.entity

import kotlinx.serialization.Serializable

@Serializable
data class CreatedDevice(var id: Long, var type: String)

@Serializable
data class DeviceRequestData(var username: String, var password: String, var device: String)

@Serializable
data class DeviceCondition(var username: String, var password: String, var id: Long)

@Serializable
data class DeviceType(var type: String)

@Serializable
data class DeviceSettingValue(var value: Int)

@Serializable
data class DeviceSettingMode(var mode: String)