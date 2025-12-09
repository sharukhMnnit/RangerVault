package com.example.rangervault.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    private const val BASE_URL = "https://seemed-native-may-nails.trycloudflare.com"

    val api: RangerApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RangerApi::class.java)
    }
}