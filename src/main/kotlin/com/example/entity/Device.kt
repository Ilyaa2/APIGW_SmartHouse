package com.example.entity

import kotlinx.serialization.Serializable


@Serializable
data class AllDevicesResponse(var req: Long, var data: Array<CreatedDevice>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AllDevicesResponse

        if (req != other.req) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = req.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

@Serializable
data class CreatedDeviceResponse(var req: Long, var data: CreatedDevice)

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