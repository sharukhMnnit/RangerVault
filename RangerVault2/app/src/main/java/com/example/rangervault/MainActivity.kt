package com.example.rangervault

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.QrCodeScanner

// --- EXPLICIT MATERIAL 3 IMPORTS (Fixes TopAppBar/Scaffold errors) ---
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults

// --- EXPLICIT SCREEN IMPORTS (Fixes LockScreen/LoginScreen red text) ---
import com.example.rangervault.ui.screens.AdminDashboardScreen
import com.example.rangervault.ui.screens.IdGeneratorScreen
import com.example.rangervault.ui.screens.LockScreen
import com.example.rangervault.ui.screens.LoginScreen
import com.example.rangervault.ui.screens.ResultScreen
import com.example.rangervault.ui.screens.ScannerScreen
import com.example.rangervault.ui.theme.RangerVaultTheme
import com.example.rangervault.viewmodel.RangerViewModel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.journeyapps.barcodescanner.ScanContract

class MainActivity : AppCompatActivity() {

    private val viewModel: RangerViewModel by viewModels()

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.processScanResult(result.contents)
            playSound(viewModel.lastScanSuccess)
            vibratePhone(viewModel.lastScanSuccess)
            getLocationAndLog(viewModel.lastScannedUser, "SCANNED", viewModel.lastScanSuccess)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)

        setContent {
            RangerVaultTheme {
                if (!viewModel.isAuthenticated) {
                    LockScreen(
                        onUnlock = { showBiometricPrompt() },
                        onBypass = { viewModel.isAuthenticated = true }
                    )
                } else if (!viewModel.isLoggedIn) {
                    LoginScreen(viewModel)
                } else {
                    MainAppNav()
                }
            }
        }
    }

    // This annotation allows us to use the new TopAppBar
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainAppNav() {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val startDest = if (viewModel.currentUserRole == "Commander") "admin_dashboard" else "id_screen"

        LaunchedEffect(viewModel.pendingNavigation) {
            viewModel.pendingNavigation?.let {
                navController.navigate(it)
                viewModel.clearNavigation()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(viewModel.currentUserName, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                    actions = { IconButton(onClick = { viewModel.logout() }) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = Color.Gray) } }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.Black) {
                    if (viewModel.currentUserRole == "Commander") {
                        NavigationBarItem(
                            selected = currentRoute == "admin_dashboard",
                            onClick = { navController.navigate("admin_dashboard") },
                            icon = { Icon(Icons.Default.Dashboard, null) },
                            label = { Text("Admin") },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    NavigationBarItem(
                        selected = currentRoute == "id_screen",
                        onClick = { navController.navigate("id_screen") },
                        icon = { Icon(Icons.Default.Fingerprint, null) },
                        label = { Text("ID") },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary)
                    )
                    NavigationBarItem(
                        selected = currentRoute == "scan_screen",
                        onClick = { navController.navigate("scan_screen") },
                        icon = { Icon(Icons.Default.QrCodeScanner, null) },
                        label = { Text("Scan") },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }
        ) { p ->
            NavHost(navController, startDest, Modifier.padding(p)) {
                composable("admin_dashboard") { AdminDashboardScreen(viewModel) }
                composable("id_screen") {
                    IdGeneratorScreen(viewModel, onGeoLogReq = {
                        getLocationAndLog(viewModel.currentUserName, "CREATED", true)
                    })
                }
                composable("scan_screen") { ScannerScreen { options -> scanLauncher.launch(options) } }
                composable("result_screen") {
                    ResultScreen(
                        isSuccess = viewModel.lastScanSuccess,
                        username = viewModel.lastScannedUser,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    private fun getLocationAndLog(userId: String, actionType: String, isSuccess: Boolean) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                viewModel.addLogEntry(loc?.latitude ?: 0.0, loc?.longitude ?: 0.0, actionType, isSuccess, userId)
            }
        } else {
            viewModel.addLogEntry(0.0, 0.0, actionType, isSuccess, userId)
        }
    }

    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
            viewModel.isAuthenticated = true; return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.isAuthenticated = true; playSound(true)
            }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Black Ranger Ops").setNegativeButtonText("Cancel").build())
    }

    private fun playSound(isSuccess: Boolean) {
        try { MediaPlayer.create(this, if (isSuccess) R.raw.scan_success else R.raw.scan_fail).start() } catch (e: Exception) {}
    }

    private fun vibratePhone(isSuccess: Boolean) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(if (isSuccess) 100 else 500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(if (isSuccess) 100 else 500)
        }
    }
}