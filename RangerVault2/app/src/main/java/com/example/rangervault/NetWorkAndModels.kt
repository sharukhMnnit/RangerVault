                                                                                    package com.example.rangervault

                                                                                    import retrofit2.Retrofit
                                                                                    import retrofit2.converter.gson.GsonConverterFactory
                                                                                    import retrofit2.http.Body
                                                                                    import retrofit2.http.POST
                                                                                    import okhttp3.ResponseBody

                                                                                    // --- 1. ADVANCED DATA MODELS ---
                                                                                    // We added 'deviceId' to prevent cloning
                                                                                    data class IdentityRequest(val userId: String, val role: String, val deviceId: String)
                                                                                    data class IdentityResponse(val payload: String, val signature: String)

                                                                                    // We added 'lat' and 'lng' for Geo-Fencing
                                                                                    data class LogRequest(
                                                                                        val userId: String,
                                                                                        val status: String,
                                                                                        val lat: Double,
                                                                                        val lng: Double,
                                                                                        val deviceId: String
                                                                                    )

                                                                                    // --- 2. API INTERFACE ---
                                                                                    interface RangerApi {
                                                                                        @POST("/api/generate-identity")
                                                                                        suspend fun getIdentity(@Body request: IdentityRequest): IdentityResponse

                                                                                        @POST("/api/log-entry")
    suspend fun sendLog(@Body request: LogRequest): ResponseBody
}

// --- 3. NETWORK CLIENT ---
object NetworkClient {
    // ⚠️ REPLACE WITH YOUR LAPTOP IP (e.g. 192.168.1.5:3000)
    // If using Emulator, use "10.0.2.2:3000"
    private const val BASE_URL = "https://sword-folding-sees-karl.trycloudflare.com" // <-- UPDATE THIS

    val api: RangerApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RangerApi::class.java)
    }
}











//package com.example.rangervault
//
//
//import okhttp3.ResponseBody
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//import retrofit2.http.Body
//import retrofit2.http.POST
//
//// --- 1. DATA MODELS ---
//data class IdentityRequest(val userId: String, val role: String)
//data class IdentityResponse(val payload: String, val signature: String)
//data class LogRequest(val userId: String, val status: String)
//
//// --- 2. API INTERFACE ---
//interface RangerApi {
//    @POST("/api/generate-identity")
//    suspend fun getIdentity(@Body request: IdentityRequest): IdentityResponse
//
//    @POST("/api/log-entry")
//    suspend fun sendLog(@Body request: LogRequest): ResponseBody
//}
//
//// --- 3. NETWORK CLIENT ---
//object NetworkClient {
//    // IMPORTANT:
//    // Use "10.0.2.2:3000" if running on Android Emulator
//    // Use "YOUR_LAPTOP_IP:3000" (e.g., 192.168.1.5:3000) if running on a Real Phone
//    private const val BASE_URL = "http://10.0.2.2:3000/"
//
//    val api: RangerApi by lazy {
//        Retrofit.Builder()
//            .baseUrl(BASE_URL)
//            .addConverterFactory(GsonConverterFactory.create())
//            .build()
//            .create(RangerApi::class.java)
//    }
//}