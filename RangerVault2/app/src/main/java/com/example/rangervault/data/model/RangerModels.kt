package com.example.rangervault.data.model

data class IdentityRequest(val userId: String, val role: String, val deviceId: String)
data class IdentityResponse(val payload: String, val signature: String)

data class LogRequest(
    val userId: String, val status: String, val lat: Double, val lng: Double, val deviceId: String
)

data class GeoLogEntry(
    val user: String, val action: String, val lat: Double, val lng: Double,
    val time: String, val wasSuccess: Boolean, val deviceId: String
)