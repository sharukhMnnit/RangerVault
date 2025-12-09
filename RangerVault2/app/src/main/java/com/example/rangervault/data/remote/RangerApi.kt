package com.example.rangervault.data.remote

import com.example.rangervault.data.model.*
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST

interface RangerApi {
    @POST("/api/generate-identity")
    suspend fun getIdentity(@Body request: IdentityRequest): IdentityResponse

    @POST("/api/log-entry")
    suspend fun sendLog(@Body request: LogRequest): ResponseBody
}