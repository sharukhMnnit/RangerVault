package com.example.rangervault.data.repository

import android.content.Context
import com.example.rangervault.data.model.*
import com.example.rangervault.data.remote.NetworkClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RangerRepository(private val context: Context) {
    private val sharedPref = context.getSharedPreferences("RangerData", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveLogs(logs: List<GeoLogEntry>, success: Int, fail: Int, total: Int) {
        val editor = sharedPref.edit()
        editor.putString("logs_key", gson.toJson(logs))
        editor.putInt("success_count", success)
        editor.putInt("fail_count", fail)
        editor.putInt("total_count", total)
        editor.apply()
    }

    fun loadLogs(): List<GeoLogEntry> {
        val json = sharedPref.getString("logs_key", null) ?: return emptyList()
        val type = object : TypeToken<List<GeoLogEntry>>() {}.type
        return gson.fromJson(json, type)
    }

    fun loadStats(): Triple<Int, Int, Int> {
        val s = sharedPref.getInt("success_count", 0)
        val f = sharedPref.getInt("fail_count", 0)
        val t = sharedPref.getInt("total_count", 0)
        return Triple(s, f, t)
    }

    fun clearData() = sharedPref.edit().clear().apply()

    suspend fun fetchIdentity(req: IdentityRequest) = withContext(Dispatchers.IO) {
        NetworkClient.api.getIdentity(req)
    }

    suspend fun uploadLog(req: LogRequest) = withContext(Dispatchers.IO) {
        try { NetworkClient.api.sendLog(req) } catch (e: Exception) { }
    }

    fun getCurrentTime(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}