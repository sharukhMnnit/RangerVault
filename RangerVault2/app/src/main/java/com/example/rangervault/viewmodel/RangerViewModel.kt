package com.example.rangervault.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rangervault.data.model.GeoLogEntry
import com.example.rangervault.data.model.IdentityRequest
import com.example.rangervault.data.model.LogRequest
import com.example.rangervault.data.repository.RangerRepository
import com.example.rangervault.utils.OfflineVerifier
import com.example.rangervault.utils.QRGenerator
import kotlinx.coroutines.launch

class RangerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RangerRepository(application)
    private val contentResolver = application.contentResolver

    // --- STATE ---
    var isAuthenticated by mutableStateOf(false)
    var isLoggedIn by mutableStateOf(false)
    var currentUserName by mutableStateOf("")
    var currentUserRole by mutableStateOf("Ranger")

    // Stats & History
    var totalScans by mutableStateOf(0)
    var successfulScans by mutableStateOf(0)
    var failedScans by mutableStateOf(0)
    val scanHistory = mutableStateListOf<Pair<String, Boolean>>()
    val globalGeoLogs = mutableStateListOf<GeoLogEntry>()

    // Navigation & Results
    var lastScanSuccess by mutableStateOf(false)
    var lastScannedUser by mutableStateOf("")
    var pendingNavigation by mutableStateOf<String?>(null)

    // QR State
    var qrBitmap by mutableStateOf<Bitmap?>(null)

    init {
        loadData()
    }

    private fun loadData() {
        val stats = repository.loadStats()
        successfulScans = stats.first
        failedScans = stats.second
        totalScans = stats.third

        globalGeoLogs.clear()
        globalGeoLogs.addAll(repository.loadLogs())
    }

    fun login(name: String, role: String) {
        currentUserName = name
        currentUserRole = role
        isLoggedIn = true
    }

    fun logout() {
        isLoggedIn = false
        currentUserName = ""
    }

    fun processScanResult(rawContent: String) {
        val isValid = OfflineVerifier.verify(rawContent)
        val payload = rawContent.split("##")[0]
        val scannedUserId = try { payload.split("|")[0] } catch (e: Exception) { "Unknown" }

        lastScanSuccess = isValid
        lastScannedUser = scannedUserId
        pendingNavigation = "result_screen"

        scanHistory.add(0, scannedUserId to isValid)
        totalScans++
        if (isValid) successfulScans++ else failedScans++
    }

    fun addLogEntry(lat: Double, lng: Double, actionType: String, isSuccess: Boolean, userId: String) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "Offline"
        val timeNow = repository.getCurrentTime()
        val entry = GeoLogEntry(userId, actionType, lat, lng, timeNow, isSuccess, deviceId)

        globalGeoLogs.add(0, entry)
        repository.saveLogs(globalGeoLogs, successfulScans, failedScans, totalScans)

        // Send to API
        viewModelScope.launch {
            repository.uploadLog(LogRequest(userId, actionType, lat, lng, deviceId))
        }
    }

    fun generateIdentityToken() {
        viewModelScope.launch {
            try {
                val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                val response = repository.fetchIdentity(IdentityRequest(currentUserName, currentUserRole, deviceId))
                qrBitmap = QRGenerator.generate("${response.payload}##${response.signature}")
            } catch (e: Exception) {
                // Optional: Handle network error state here
            }
        }
    }

    fun purgeSystem() {
        repository.clearData()
        globalGeoLogs.clear()
        scanHistory.clear()
        successfulScans = 0; failedScans = 0; totalScans = 0
    }

    fun clearNavigation() { pendingNavigation = null }
}