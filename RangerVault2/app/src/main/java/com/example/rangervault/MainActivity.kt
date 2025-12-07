package com.example.rangervault

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- CUSTOM ACTIVITY TO FORCE PORTRAIT MODE ---
class PortraitCaptureActivity : CaptureActivity()

// --- DATA CLASS FOR LOCAL UI LOGS ---
data class GeoLogEntry(
    val user: String,
    val action: String,
    val lat: Double,
    val lng: Double,
    val time: String,
    val wasSuccess: Boolean,
    val deviceId: String
)

class MainActivity : AppCompatActivity() {

    // --- STATE ---
    var isAuthenticated by mutableStateOf(false)
    var isLoggedIn by mutableStateOf(false)
    var currentUserName by mutableStateOf("")
    var currentUserRole by mutableStateOf("Ranger")

    // --- NEW VARIABLES FOR RESULT SCREEN ---
    var lastScanSuccess by mutableStateOf(false)
    var lastScannedUser by mutableStateOf("")
    var pendingNavigation by mutableStateOf<String?>(null)

    // --- DATA ---
    val scanHistory = mutableStateListOf<Pair<String, Boolean>>()
    val globalGeoLogs = mutableStateListOf<GeoLogEntry>()

    // --- ANALYTICS ---
    var totalScans by mutableStateOf(0)
    var successfulScans by mutableStateOf(0)
    var failedScans by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadLogsFromStorage()
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        setContent {
            RangerVaultTheme {
                if (!isAuthenticated) LockScreen({ showBiometricPrompt() }, { isAuthenticated = true })
                else if (!isLoggedIn) LoginScreen { name, role -> currentUserName = name; currentUserRole = role; isLoggedIn = true }
                else MainScreen(this)
            }
        }
    }

    fun saveLogsToStorage() {
        val sharedPref = getSharedPreferences("RangerData", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        val gson = Gson()
        editor.putString("logs_key", gson.toJson(globalGeoLogs))
        editor.putInt("success_count", successfulScans)
        editor.putInt("fail_count", failedScans)
        editor.putInt("total_count", totalScans)
        editor.apply()
    }

    fun loadLogsFromStorage() {
        val sharedPref = getSharedPreferences("RangerData", Context.MODE_PRIVATE)
        val gson = Gson()
        successfulScans = sharedPref.getInt("success_count", 0)
        failedScans = sharedPref.getInt("fail_count", 0)
        totalScans = sharedPref.getInt("total_count", 0)

        val json = sharedPref.getString("logs_key", null)
        if (json != null) {
            val type = object : TypeToken<List<GeoLogEntry>>() {}.type
            val savedList: List<GeoLogEntry> = gson.fromJson(json, type)
            globalGeoLogs.clear()
            globalGeoLogs.addAll(savedList)
        }
    }

    fun clearAllData() {
        val sharedPref = getSharedPreferences("RangerData", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()
        globalGeoLogs.clear()
        scanHistory.clear()
        successfulScans = 0; failedScans = 0; totalScans = 0
        Toast.makeText(this, "SYSTEM PURGED", Toast.LENGTH_SHORT).show()
    }

    fun logout() { isLoggedIn = false; currentUserName = "" }

    fun getLocationAndLog(userId: String, actionType: String, isSuccess: Boolean) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "Offline-Device"
        val timeNow = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        fun saveAndShow(lat: Double, lng: Double) {
            runOnUiThread {
                val entry = GeoLogEntry(userId, actionType, lat, lng, timeNow, isSuccess, deviceId)
                globalGeoLogs.add(0, entry)
                saveLogsToStorage()
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                saveAndShow(loc?.latitude ?: 0.0, loc?.longitude ?: 0.0)
                sendToBackend(userId, actionType, loc?.latitude ?: 0.0, loc?.longitude ?: 0.0, deviceId)
            }
        } else {
            saveAndShow(0.0, 0.0)
            sendToBackend(userId, actionType, 0.0, 0.0, deviceId)
        }
    }

    private fun sendToBackend(userId: String, status: String, lat: Double, lng: Double, deviceId: String) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try { NetworkClient.api.sendLog(LogRequest(userId, status, lat, lng, deviceId)) } catch (e: Exception) { }
        }
    }

    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val isValid = OfflineVerifier.verify(result.contents)
            playSound(isValid); vibratePhone(isValid)
            val payload = result.contents.split("##")[0]
            val scannedUserId = try { payload.split("|")[0] } catch (e: Exception) { "Unknown" }

            lastScanSuccess = isValid
            lastScannedUser = scannedUserId
            pendingNavigation = "result_screen"

            scanHistory.add(0, scannedUserId to isValid)
            totalScans++; if(isValid) successfulScans++ else failedScans++
            getLocationAndLog(scannedUserId, "SCANNED", isValid)
        }
    }

    fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) { isAuthenticated = true; return }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { isAuthenticated = true; playSound(true) }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Black Ranger Ops").setNegativeButtonText("Cancel").build())
    }

    fun playSound(isSuccess: Boolean) { try { MediaPlayer.create(this, if (isSuccess) R.raw.scan_success else R.raw.scan_fail).start() } catch (e: Exception) {} }

    // --- VIBRATION FIX ---
    fun vibratePhone(isSuccess: Boolean) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(if (isSuccess) 100 else 500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(if (isSuccess) 100 else 500)
        }
    }
}

@Composable
fun RangerVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFFFD700),
        onPrimary = Color.Black,
        background = Color(0xFF000000),
        surface = Color(0xFF121212)
    )) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
}

@Composable
fun LockScreen(onUnlock: () -> Unit, onBypass: () -> Unit) {
    LaunchedEffect(Unit) { delay(800); onUnlock() }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("BLACK RANGER OPS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onUnlock, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text("INITIATE PROTOCOL", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onBypass) { Text("Override", color = Color.DarkGray) }
        }
    }
}

@Composable
fun LoginScreen(onLogin: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Ranger") }
    BackHandler(enabled = true) { }

    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("IDENTITY LOGIN", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Operative Name") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, focusedLabelColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            FilterChip(selected = role == "Ranger", onClick = { role = "Ranger" }, label = { Text("Ranger") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary))
            FilterChip(selected = role == "Commander", onClick = { role = "Commander" }, label = { Text("Commander") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary))
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { if (name.isNotEmpty()) onLogin(name, role) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("ACCESS SYSTEM", color = Color.Black, fontWeight = FontWeight.Bold) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(activity: MainActivity) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val startDest = if (activity.currentUserRole == "Commander") "admin_dashboard" else "id_screen"

    LaunchedEffect(activity.pendingNavigation) {
        activity.pendingNavigation?.let {
            navController.navigate(it)
            activity.pendingNavigation = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(activity.currentUserName, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                actions = { IconButton(onClick = { activity.logout() }) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = Color.Gray) } }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.Black) {
                if (activity.currentUserRole == "Commander") NavigationBarItem(selected = currentRoute=="admin_dashboard", onClick={navController.navigate("admin_dashboard")}, icon={Icon(Icons.Default.Dashboard,null)}, label={Text("Admin")}, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary))
                NavigationBarItem(selected = currentRoute=="id_screen", onClick={navController.navigate("id_screen")}, icon={Icon(Icons.Default.Fingerprint,null)}, label={Text("ID")}, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary))
                NavigationBarItem(selected = currentRoute=="scan_screen", onClick={navController.navigate("scan_screen")}, icon={Icon(Icons.Default.QrCodeScanner,null)}, label={Text("Scan")}, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary))
            }
        }
    ) { p ->
        NavHost(navController, startDest, Modifier.padding(p)) {
            composable("admin_dashboard") { AdminDashboardScreen(activity) }
            composable("id_screen") { IdGeneratorScreen(activity) }
            composable("scan_screen") { ScannerScreen(activity) }
            composable("result_screen") {
                ResultScreen(
                    isSuccess = activity.lastScanSuccess,
                    username = activity.lastScannedUser,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun AdminDashboardScreen(activity: MainActivity) {
    val context = LocalContext.current
    BackHandler { activity.logout() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("COMMANDER OPS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Button(onClick = { activity.clearAllData() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF330000)), contentPadding = PaddingValues(horizontal=8.dp)) {
                Icon(Icons.Default.DeleteForever, null, tint = Color.Red); Text("PURGE", color = Color.Red)
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)), modifier = Modifier.fillMaxWidth().padding(bottom=16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Verification Status", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Total: ${activity.totalScans}", color = Color.Gray)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(4.dp)).background(Color.DarkGray)) {
                    if (activity.totalScans > 0) {
                        val successWeight = activity.successfulScans.toFloat() / activity.totalScans.toFloat()
                        val failWeight = activity.failedScans.toFloat() / activity.totalScans.toFloat()
                        if (successWeight > 0) Box(Modifier.weight(successWeight).fillMaxHeight().background(Color(0xFF00C853)))
                        if (failWeight > 0) Box(Modifier.weight(failWeight).fillMaxHeight().background(Color(0xFFD50000)))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("✔ ${activity.successfulScans} Granted", color = Color(0xFF00C853), fontSize = 12.sp)
                    Text("✖ ${activity.failedScans} Denied", color = Color(0xFFD50000), fontSize = 12.sp)
                }
            }
        }
        Text("FIELD LOGS (OFFLINE)", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(activity.globalGeoLogs.size) { i ->
                val log = activity.globalGeoLogs[i]
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), modifier = Modifier.fillMaxWidth().padding(vertical=4.dp).clickable {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${log.lat},${log.lng}?q=${log.lat},${log.lng}(${log.user})")).setPackage("com.google.android.apps.maps")) } catch(e: Exception){}
                }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if(log.action=="CREATED") Icons.Default.AddCircle else Icons.Default.QrCodeScanner, null, tint = if(log.action=="CREATED") Color.Cyan else (if(log.wasSuccess) MaterialTheme.colorScheme.primary else Color.Red), modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${log.user} [${log.action}]", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Lat: ${log.lat}, Lng: ${log.lng}", color = Color.LightGray, fontSize = 11.sp)
                            Text("${log.time} | ID: ${log.deviceId.take(4)}", color = Color.DarkGray, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IdGeneratorScreen(activity: MainActivity) {
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var timeLeft by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(qrBitmap) { if (qrBitmap != null) { timeLeft = 30; while (timeLeft > 0) { delay(1000L); timeLeft-- }; qrBitmap = null } }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (qrBitmap != null) {
            Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)))
            Text("${timeLeft}s", color = MaterialTheme.colorScheme.primary, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top=16.dp))
            Text("TOKEN EXPIRING", color = Color.Gray, fontSize = 12.sp)
        } else {
            Icon(Icons.Default.Shield, null, Modifier.size(80.dp), tint = Color.DarkGray)
            Spacer(Modifier.height(16.dp))
            Text(activity.currentUserName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Security Level: ${activity.currentUserRole}", color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                scope.launch {
                    try {
                        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                        val response = NetworkClient.api.getIdentity(IdentityRequest(activity.currentUserName, activity.currentUserRole, deviceId))
                        qrBitmap = generateQRCode("${response.payload}##${response.signature}")
                        activity.getLocationAndLog(activity.currentUserName, "CREATED", true)
                    } catch (e: Exception) { Toast.makeText(context, "Network Link Failed", Toast.LENGTH_SHORT).show() }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("GENERATE SECURE TOKEN", color = Color.Black, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun ScannerScreen(activity: MainActivity) {
    val infiniteTransition = rememberInfiniteTransition()
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse)
    )

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(250.dp).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)).clickable {
            val options = ScanOptions()
            options.setCaptureActivity(PortraitCaptureActivity::class.java)
            options.setOrientationLocked(true)
            options.setBeepEnabled(false)
            activity.scanLauncher.launch(options)
        }, contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(2.dp).offset(y = offsetY.dp).background(Color.Red))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.QrCodeScanner, null, tint = Color.Gray, modifier = Modifier.size(50.dp))
                Text("Tap to Scan", color = Color.Gray)
            }
        }
    }
}

@Composable
fun ResultScreen(isSuccess: Boolean, username: String, onBack: () -> Unit) {
    val bgColor = if (isSuccess) Color(0xFF004D40) else Color(0xFF4A0000)
    val iconColor = if (isSuccess) Color(0xFF00E676) else Color(0xFFFF1744)
    val mainText = if (isSuccess) "ACCESS GRANTED" else "ACCESS DENIED"
    val subText = if (isSuccess) "Identity Verified" else "Security Protocol Violation"
    val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Close

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(32.dp))
        Text(mainText, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(subText, color = Color.LightGray, fontSize = 14.sp)
        Spacer(Modifier.height(48.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f))) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("OPERATIVE ID", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(username, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(64.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("RETURN TO SCANNER", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

fun generateQRCode(content: String): Bitmap? {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
    val width = bitMatrix.width; val height = bitMatrix.height
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) { for (y in 0 until height) { bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE) } }
    return bmp
}


////package com.example.rangervault
////
////import android.Manifest
////import androidx.compose.ui.unit.sp
////import android.content.Context
////import android.content.pm.PackageManager
////import android.graphics.Bitmap
////import android.graphics.Color as AndroidColor
////import android.media.MediaPlayer
////import android.os.Build
////import android.os.Bundle
////import android.os.VibrationEffect
////import android.os.Vibrator
////import android.provider.Settings
////import android.widget.Toast
////import androidx.activity.compose.setContent
////import androidx.appcompat.app.AppCompatActivity
////import androidx.biometric.BiometricManager
////import androidx.biometric.BiometricPrompt
////import androidx.compose.animation.core.*
////import androidx.compose.foundation.Image
////import androidx.compose.foundation.background
////import androidx.compose.foundation.border
////import androidx.compose.foundation.clickable
////import androidx.compose.foundation.layout.*
////import androidx.compose.foundation.lazy.LazyColumn
////import androidx.compose.foundation.shape.RoundedCornerShape
////import androidx.compose.material.icons.Icons
////import androidx.compose.material.icons.filled.*
////import androidx.compose.material3.*
////import androidx.compose.runtime.*
////import androidx.compose.ui.Alignment
////import androidx.compose.ui.Modifier
////import androidx.compose.ui.draw.clip
////import androidx.compose.ui.graphics.Brush
////import androidx.compose.ui.graphics.Color
////import androidx.compose.ui.graphics.asImageBitmap
////import androidx.compose.ui.graphics.vector.ImageVector
////import androidx.compose.ui.platform.LocalContext
////import androidx.compose.ui.text.font.FontWeight
////import androidx.compose.ui.unit.dp
////import androidx.core.app.ActivityCompat
////import androidx.core.content.ContextCompat
////import androidx.navigation.compose.NavHost
////import androidx.navigation.compose.composable
////import androidx.navigation.compose.currentBackStackEntryAsState
////import androidx.navigation.compose.rememberNavController
////import com.google.android.gms.location.LocationServices
////import com.google.zxing.BarcodeFormat
////import com.google.zxing.qrcode.QRCodeWriter
////import com.journeyapps.barcodescanner.CaptureActivity
////import com.journeyapps.barcodescanner.ScanContract
////import com.journeyapps.barcodescanner.ScanOptions
////import kotlinx.coroutines.Dispatchers
////import kotlinx.coroutines.delay
////import kotlinx.coroutines.launch
////import java.text.SimpleDateFormat
////import java.util.Date
////import java.util.Locale
////
////class MainActivity : AppCompatActivity() {
////
////    var isAuthenticated by mutableStateOf(false)
////    val scanHistory = mutableStateListOf<Pair<String, Boolean>>()
////    val generationHistory = mutableStateListOf<Pair<String, String>>()
////
////    // --- GEO-LOCATION LOGIC ---
////    private fun getLocationAndLog(userId: String, status: String) {
////        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
////        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
////
////        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
////            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
////                val lat = location?.latitude ?: 0.0
////                val lng = location?.longitude ?: 0.0
////
////                // Send to Server
////                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
////                    try {
////                        NetworkClient.api.sendLog(LogRequest(userId, status, lat, lng, deviceId))
////                    } catch (e: Exception) { e.printStackTrace() }
////                }
////            }
////        } else {
////            // Permission missing, send 0.0
////            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
////                try {
////                    NetworkClient.api.sendLog(LogRequest(userId, status, 0.0, 0.0, deviceId))
////                } catch (e: Exception) { e.printStackTrace() }
////            }
////        }
////    }
////
////    // --- SCANNER LAUNCHER ---
////    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
////        if (result.contents != null) {
////            val isValid = OfflineVerifier.verify(result.contents)
////            playSound(isValid)
////            vibratePhone(isValid)
////
////            val payload = result.contents.split("##")[0]
////            val userId = try { payload.split("|")[0] } catch (e: Exception) { "Unknown" }
////
////            scanHistory.add(0, userId to isValid)
////
////            // Trigger Geo-Log
////            getLocationAndLog(userId, if (isValid) "GRANTED" else "DENIED")
////        }
////    }
////
////    // --- BIOMETRICS (Keep existing logic) ---
////    fun showBiometricPrompt() {
////        val biometricManager = BiometricManager.from(this)
////        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
////            BiometricManager.BIOMETRIC_SUCCESS -> {}
////            else -> {
////                Toast.makeText(this, "Biometrics Bypassed (Dev Mode)", Toast.LENGTH_SHORT).show()
////                isAuthenticated = true
////                return
////            }
////        }
////        try {
////            val executor = ContextCompat.getMainExecutor(this)
////            val biometricPrompt = BiometricPrompt(this, executor,
////                object : BiometricPrompt.AuthenticationCallback() {
////                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
////                        super.onAuthenticationSucceeded(result)
////                        isAuthenticated = true
////                        playSound(true)
////                    }
////                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
////                        super.onAuthenticationError(errorCode, errString)
////                        Toast.makeText(applicationContext, "Error: $errString", Toast.LENGTH_SHORT).show()
////                    }
////                })
////            val promptInfo = BiometricPrompt.PromptInfo.Builder()
////                .setTitle("RangerVault Security")
////                .setSubtitle("Confirm Identity")
////                .setNegativeButtonText("Cancel")
////                .build()
////            biometricPrompt.authenticate(promptInfo)
////        } catch (e: Exception) {
////            isAuthenticated = true
////        }
////    }
////
////    fun playSound(isSuccess: Boolean) {
////        try {
////            val soundId = if (isSuccess) R.raw.scan_success else R.raw.scan_fail
////            val mediaPlayer = MediaPlayer.create(this, soundId)
////            mediaPlayer.start()
////            mediaPlayer.setOnCompletionListener { it.release() }
////        } catch (e: Exception) {}
////    }
////
////    fun vibratePhone(isSuccess: Boolean) {
////        try {
////            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
////            if (Build.VERSION.SDK_INT >= 26) {
////                val amplitude = if (isSuccess) 50 else VibrationEffect.DEFAULT_AMPLITUDE
////                val duration = if (isSuccess) 100L else 500L
////                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
////            } else { vibrator.vibrate(200) }
////        } catch (e: Exception) {}
////    }
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////
////        // Request Location Permission on Startup
////        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
////
////        setContent {
////            RangerVaultTheme {
////                if (isAuthenticated) {
////                    MainScreen(this)
////                } else {
////                    LockScreen(
////                        onUnlockClick = { showBiometricPrompt() },
////                        onEmergencyBypass = { isAuthenticated = true }
////                    )
////                }
////            }
////        }
////    }
////}
////
////// --- THEME ---
////@Composable
////fun RangerVaultTheme(content: @Composable () -> Unit) {
////    MaterialTheme(
////        colorScheme = darkColorScheme(
////            primary = Color(0xFFE53935),
////            onPrimary = Color.White,
////            background = Color(0xFF000000),
////            surface = Color(0xFF121212),
////            surfaceVariant = Color(0xFF222222)
////        )
////    ) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
////}
////
////// --- LOCK SCREEN ---
////@Composable
////fun LockScreen(onUnlockClick: () -> Unit, onEmergencyBypass: () -> Unit) {
////    LaunchedEffect(Unit) { delay(800); onUnlockClick() }
////    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Black, Color(0xFF330000)))), contentAlignment = Alignment.Center) {
////        Column(horizontalAlignment = Alignment.CenterHorizontally) {
////            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(100.dp))
////            Spacer(modifier = Modifier.height(20.dp))
////            Text("RANGER VAULT", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = Color.White)
////            Text("SECURE ACCESS TERMINAL", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, letterSpacing = 2.sp)
////            Spacer(modifier = Modifier.height(50.dp))
////            Button(onClick = onUnlockClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
////                Icon(Icons.Default.Fingerprint, null)
////                Spacer(modifier = Modifier.width(8.dp))
////                Text("AUTHENTICATE")
////            }
////            Spacer(modifier = Modifier.height(20.dp))
////            TextButton(onClick = onEmergencyBypass) { Text("Dev Override", color = Color.DarkGray) }
////        }
////    }
////}
////
////// --- MAIN SCREEN ---
////@OptIn(ExperimentalMaterial3Api::class)
////@Composable
////fun MainScreen(activity: MainActivity) {
////    val navController = rememberNavController()
////    val navBackStackEntry by navController.currentBackStackEntryAsState()
////    val currentRoute = navBackStackEntry?.destination?.route
////
////    Scaffold(
////        bottomBar = {
////            NavigationBar(containerColor = Color.Black) {
////                NavigationBarItem(selected = currentRoute=="id_screen", onClick={navController.navigate("id_screen")}, icon={Icon(Icons.Default.Fingerprint,null)}, label={Text("My ID")})
////                NavigationBarItem(selected = currentRoute=="scan_screen", onClick={navController.navigate("scan_screen")}, icon={Icon(Icons.Default.QrCodeScanner,null)}, label={Text("Scan")})
////                NavigationBarItem(selected = currentRoute=="history_screen", onClick={navController.navigate("history_screen")}, icon={Icon(Icons.Default.History,null)}, label={Text("Logs")})
////            }
////        }
////    ) { padding ->
////        NavHost(navController, startDestination = "id_screen", modifier = Modifier.padding(padding)) {
////            composable("id_screen") { IdGeneratorScreen(activity) }
////            composable("scan_screen") { ScannerScreen(activity) }
////            composable("history_screen") { HistoryScreen(activity.scanHistory, activity.generationHistory) }
////        }
////    }
////}
////
////@Composable
////fun IdGeneratorScreen(activity: MainActivity) {
////    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
////    var timeLeft by remember { mutableStateOf(0) }
////    val scope = rememberCoroutineScope()
////    val context = LocalContext.current
////
////    LaunchedEffect(qrBitmap) {
////        if (qrBitmap != null) {
////            timeLeft = 30
////            while (timeLeft > 0) {
////                delay(1000L)
////                timeLeft--
////            }
////            qrBitmap = null
////        }
////    }
////
////    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
////        Card(
////            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
////            shape = RoundedCornerShape(16.dp),
////            modifier = Modifier.size(320.dp).border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
////        ) {
////            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
////                if (qrBitmap != null) {
////                    Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)))
////                    CircularProgressIndicator(progress = { timeLeft / 30f }, modifier = Modifier.size(280.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 8.dp)
////                    Text("${timeLeft}s", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
////                } else {
////                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
////                        Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = Color.Gray)
////                        Text("Awaiting Clearance", color = Color.Gray, modifier = Modifier.padding(top=8.dp))
////                    }
////                }
////            }
////        }
////        Spacer(modifier = Modifier.height(40.dp))
////        Button(
////            onClick = {
////                activity.vibratePhone(true)
////                scope.launch {
////                    try {
////                        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
////                        val response = NetworkClient.api.getIdentity(IdentityRequest("Ranger_Red", "Commander", deviceId))
////                        val finalString = "${response.payload}##${response.signature}"
////                        qrBitmap = generateQRCode(finalString)
////                        activity.generationHistory.add(0, "Commander" to SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
////
////                        // Log Generation with Location
////                        // Note: We use 0.0 for generation logs to save time, or you can implement the location fetch here too
////                        NetworkClient.api.sendLog(LogRequest("Ranger_Red", "GENERATED", 0.0, 0.0, deviceId))
////                    } catch (e: Exception) { e.printStackTrace() }
////                }
////            },
////            modifier = Modifier.fillMaxWidth().height(60.dp),
////            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
////        ) {
////            Icon(Icons.Default.VpnKey, null)
////            Spacer(modifier = Modifier.width(12.dp))
////            Text("GENERATE SECURE PASS")
////        }
////    }
////}
////
////@Composable
////fun ScannerScreen(activity: MainActivity) {
////    // Advanced UI: Animated Scanner Line
////    val infiniteTransition = rememberInfiniteTransition()
////    val offsetY by infiniteTransition.animateFloat(
////        initialValue = 0f, targetValue = 200f,
////        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse)
////    )
////
////    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
////
////        // Animated Scanner Box
////        Box(modifier = Modifier.size(280.dp).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)).clickable {
////            val options = ScanOptions()
////            options.setPrompt("Align QR Code within frame")
////            options.setBeepEnabled(false)
////            options.setCaptureActivity(CaptureActivity::class.java)
////            activity.scanLauncher.launch(options)
////        }) {
////            Box(modifier = Modifier.fillMaxWidth().height(2.dp).offset(y = offsetY.dp).background(Color.Red))
////            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
////                Icon(Icons.Default.QrCodeScanner, null, tint = Color.Gray, modifier = Modifier.size(60.dp))
////                Text("Tap to Activate", color = Color.Gray)
////            }
////        }
////
////        Spacer(modifier = Modifier.height(32.dp))
////
////        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
////            Row(modifier = Modifier.padding(16.dp)) {
////                Icon(Icons.Default.LocationOn, null, tint = Color.Green)
////                Spacer(modifier = Modifier.width(8.dp))
////                Text("Geo-Tagging: ACTIVE", color = Color.White)
////            }
////        }
////    }
////}
////
////@Composable
////fun HistoryScreen(scans: List<Pair<String, Boolean>>, generations: List<Pair<String, String>>) {
////    var selectedTab by remember { mutableStateOf(0) }
////    Column(modifier = Modifier.fillMaxSize()) {
////        TabRow(selectedTabIndex = selectedTab, containerColor = Color.Black, contentColor = Color.White) {
////            Tab(selected = selectedTab==0, onClick = { selectedTab=0 }, text = { Text("SCANS") })
////            Tab(selected = selectedTab==1, onClick = { selectedTab=1 }, text = { Text("GENERATED") })
////        }
////        LazyColumn(contentPadding = PaddingValues(16.dp)) {
////            if(selectedTab == 0) {
////                items(scans.size) { i ->
////                    val item = scans[i]
////                    HistoryItem(if(item.second) Icons.Default.CheckCircle else Icons.Default.Cancel, item.first, if(item.second) "ACCESS GRANTED" else "ACCESS DENIED", if(item.second) Color.Green else Color.Red)
////                }
////            } else {
////                items(generations.size) { i ->
////                    val item = generations[i]
////                    HistoryItem(Icons.Default.History, item.first, "Created: ${item.second}", Color.Cyan)
////                }
////            }
////        }
////    }
////}
////
////@Composable
////fun HistoryItem(icon: ImageVector, title: String, subtitle: String, color: Color) {
////    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF222222))) {
////        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
////            Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
////            Column(modifier = Modifier.padding(start=16.dp)) {
////                Text(title, fontWeight = FontWeight.Bold, color = Color.White)
////                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
////            }
////        }
////    }
////}
////
////fun generateQRCode(content: String): Bitmap? {
////    val writer = QRCodeWriter()
////    return try {
////        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
////        val width = bitMatrix.width
////        val height = bitMatrix.height
////        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
////        for (x in 0 until width) {
////            for (y in 0 until height) {
////                bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
////            }
////        }
////        bmp
////    } catch (e: Exception) { null }
////}
//
//
//
//
//
//
//
//
//
//
//
////package com.example.rangervault // <--- MAKE SURE THIS MATCHES YOUR PACKAGE NAME
////
////import android.graphics.Bitmap
////import android.graphics.Color
////import android.os.Bundle
////import android.widget.Toast
////import androidx.activity.ComponentActivity
////import androidx.activity.compose.setContent
////import androidx.compose.foundation.Image
////import androidx.compose.foundation.layout.*
////import androidx.compose.material3.*
////import androidx.compose.runtime.*
////import androidx.compose.ui.Alignment
////import androidx.compose.ui.Modifier
////import androidx.compose.ui.graphics.asImageBitmap
////import androidx.compose.ui.platform.LocalContext
////import androidx.compose.ui.unit.dp
////import kotlinx.coroutines.Dispatchers
////import kotlinx.coroutines.CoroutineScope
////import kotlinx.coroutines.launch
////import retrofit2.Retrofit
////import retrofit2.converter.gson.GsonConverterFactory
////import retrofit2.http.Body
////import retrofit2.http.POST
////
////import com.google.zxing.BarcodeFormat
////import com.google.zxing.qrcode.QRCodeWriter
////
////// Scanner Libraries
////import com.journeyapps.barcodescanner.ScanContract
////import com.journeyapps.barcodescanner.ScanOptions
////
////// 1. Define the Data Models
////data class IdentityRequest(val userId: String, val role: String)
////data class IdentityResponse(val payload: String, val signature: String)
////
////// --- NEW: Model for sending logs to Dashboard ---
////data class LogRequest(val userId: String, val status: String)
////
////// 2. Define the API Interface
////interface RangerApi {
////    @POST("/api/generate-identity")
////    suspend fun getIdentity(@Body request: IdentityRequest): IdentityResponse
////
////    // --- NEW: Function to report scans to Server ---
////    @POST("/api/log-entry")
////    suspend fun sendLog(@Body request: LogRequest): okhttp3.ResponseBody
////}
////
////// 3. Setup Retrofit (Network Client)
////// Your IP is preserved here: 172.29.59.202
////val retrofit = Retrofit.Builder()
//////    .baseUrl("http://172.29.59.202:3000/")
////    .baseUrl("http://10.0.2.2:3000/")
////    .addConverterFactory(GsonConverterFactory.create())
////    .build()
////
////val api = retrofit.create(RangerApi::class.java)
////
////class MainActivity : ComponentActivity() {
////
////    // --- SCANNER HANDLER (Updated with Logging) ---
////    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
////        if (result.contents == null) {
////            Toast.makeText(this, "Scan Cancelled", Toast.LENGTH_SHORT).show()
////        } else {
////            // 1. Verify the ID Locally (Offline Math)
////            val isValid = OfflineVerifier.verify(result.contents)
////
////            // 2. Prepare Data for Dashboard Log
////            // Structure is "User|Role|Time##Signature". We just want "User".
////            val payload = result.contents.split("##")[0]
////            val userId = try { payload.split("|")[0] } catch (e: Exception) { "Unknown" }
////            val status = if (isValid) "GRANTED" else "DENIED"
////
////            // 3. Show UI Feedback
////            if (isValid) {
////                Toast.makeText(this, "✅ ACCESS GRANTED: Valid Ranger ID", Toast.LENGTH_LONG).show()
////            } else {
////                Toast.makeText(this, "❌ ACCESS DENIED: Fake or Expired ID", Toast.LENGTH_LONG).show()
////            }
////
////            // 4. SEND LOG TO SERVER (Background Thread)
////            CoroutineScope(Dispatchers.IO).launch {
////                try {
////                    api.sendLog(LogRequest(userId, status))
////                } catch (e: Exception) {
////                    e.printStackTrace()
////                    // If server is down, we just ignore the log error. Verification still worked!
////                }
////            }
////        }
////    }
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        setContent {
////            RangerVaultApp()
////        }
////    }
////}
////
////@Composable
////fun RangerVaultApp() {
////    // UI State
////    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
////    var statusText by remember { mutableStateOf("Ready to authenticate") }
////    val scope = rememberCoroutineScope()
////
////    // We need Context to trigger the Scanner from the Button
////    val context = LocalContext.current
////
////    Column(
////        modifier = Modifier.fillMaxSize().padding(24.dp),
////        horizontalAlignment = Alignment.CenterHorizontally,
////        verticalArrangement = Arrangement.Center
////    ) {
////        Text(text = "RangerVault Identity", style = MaterialTheme.typography.headlineMedium)
////
////        Spacer(modifier = Modifier.height(32.dp))
////
////        // Show QR Code if it exists
////        if (qrBitmap != null) {
////            Image(
////                bitmap = qrBitmap!!.asImageBitmap(),
////                contentDescription = "Identity QR",
////                modifier = Modifier.size(250.dp)
////            )
////            Spacer(modifier = Modifier.height(16.dp))
////            Text("Valid for 30 seconds", color = MaterialTheme.colorScheme.primary)
////        } else {
////            // Placeholder box
////            Surface(
////                modifier = Modifier.size(250.dp),
////                color = MaterialTheme.colorScheme.surfaceVariant
////            ) {
////                Box(contentAlignment = Alignment.Center) {
////                    Text("No Active Pass")
////                }
////            }
////        }
////
////        Spacer(modifier = Modifier.height(32.dp))
////        Text(text = statusText)
////        Spacer(modifier = Modifier.height(16.dp))
////
////        // BUTTON 1: Generate ID
////        // BUTTON 1: Generate ID
////        Button(onClick = {
////            scope.launch {
////                statusText = "Contacting HQ..."
////                try {
////                    // 1. Get ID from Server
////                    val response = api.getIdentity(IdentityRequest("Ranger_Red", "Leader"))
////                    val finalString = "${response.payload}##${response.signature}"
////                    qrBitmap = generateQRCode(finalString)
////                    statusText = "Identity Secure & Active"
////
////                    // 2. --- NEW: Send a Log to Dashboard immediately! ---
////                    // This ensures you see activity on the web page even without scanning.
////                    try {
////                        api.sendLog(LogRequest("Ranger_Red", "GENERATED_NEW_ID"))
////                    } catch (e: Exception) {
////                        // Ignore log errors
////                    }
////
////                } catch (e: Exception) {
////                    e.printStackTrace()
////                    statusText = "Error: ${e.localizedMessage}"
////                }
////            }
////        }) {
////            Text("Generate Secure ID")
////        }
//////        Button(onClick = {
//////            scope.launch {
//////                statusText = "Contacting HQ..."
//////                try {
//////                    // Call the Server
//////                    val response = api.getIdentity(IdentityRequest("Ranger_Red", "Leader"))
//////
//////                    // Combine Data
//////                    val finalString = "${response.payload}##${response.signature}"
//////
//////                    // Generate QR
//////                    qrBitmap = generateQRCode(finalString)
//////                    statusText = "Identity Secure & Active"
//////
//////                } catch (e: Exception) {
//////                    e.printStackTrace()
//////                    statusText = "Error: ${e.localizedMessage}"
//////                }
//////            }
//////        }) {
//////            Text("Generate Secure ID")
//////        }
////
////        Spacer(modifier = Modifier.height(16.dp))
////
////        // BUTTON 2: Verify ID (Scanner)
////        Button(
////            onClick = {
////                val options = ScanOptions()
////                options.setPrompt("Scan Ranger ID to Verify")
////                options.setBeepEnabled(true)
////                options.setOrientationLocked(false)
////
////                // Launch the scanner defined in MainActivity
////                (context as MainActivity).scanLauncher.launch(options)
////            },
////            // Using correct Compose Color reference
////            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red)
////        ) {
////            Text("VERIFY ID (Scanner Mode)")
////        }
////    }
////}
////
////// Helper Function to draw QR
////fun generateQRCode(content: String): Bitmap? {
////    val writer = QRCodeWriter()
////    return try {
////        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
////        val width = bitMatrix.width
////        val height = bitMatrix.height
////        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
////        for (x in 0 until width) {
////            for (y in 0 until height) {
////                bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
////            }
////        }
////        bmp
////    } catch (e: Exception) {
////        null
////    }
////}
//
//
//
//
//
//
//
//
//
////package com.example.rangervault // <--- MAKE SURE THIS MATCHES YOUR PACKAGE NAME
////
////import android.graphics.Bitmap
////import android.graphics.Color
////import android.os.Bundle
////import android.widget.Toast
////import androidx.activity.ComponentActivity
////import androidx.activity.compose.setContent
////import androidx.compose.foundation.Image
////import androidx.compose.foundation.layout.*
////import androidx.compose.material3.*
////import androidx.compose.runtime.*
////import androidx.compose.ui.Alignment
////import androidx.compose.ui.Modifier
////import androidx.compose.ui.graphics.asImageBitmap
////import androidx.compose.ui.platform.LocalContext
////import androidx.compose.ui.unit.dp
////import com.google.zxing.BarcodeFormat
////import com.google.zxing.qrcode.QRCodeWriter
////import kotlinx.coroutines.launch
////import retrofit2.Retrofit
////import retrofit2.converter.gson.GsonConverterFactory
////import retrofit2.http.Body
////import retrofit2.http.POST
////
////// Scanner Libraries
////import com.journeyapps.barcodescanner.ScanContract
////import com.journeyapps.barcodescanner.ScanOptions
////
////// 1. Define the Data Models
////data class IdentityRequest(val userId: String, val role: String)
////data class IdentityResponse(val payload: String, val signature: String)
////
////// 2. Define the API Interface
////interface RangerApi {
////    @POST("/api/generate-identity")
////    suspend fun getIdentity(@Body request: IdentityRequest): IdentityResponse
////}
////
////// 3. Setup Retrofit (Network Client)
////// NOTE: Since you are on a VIVO Phone, change "10.0.2.2" to your Laptop's Wi-Fi IP (e.g., "192.168.1.X")
////val retrofit = Retrofit.Builder()
////    .baseUrl("http://172.29.59.202:3000/")
////    .addConverterFactory(GsonConverterFactory.create())
////    .build()
////
////val api = retrofit.create(RangerApi::class.java)
////
////class MainActivity : ComponentActivity() {
////
////    // --- NEW: Scanner Launcher handles the result from the Camera ---
////    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
////        if (result.contents == null) {
////            Toast.makeText(this, "Scan Cancelled", Toast.LENGTH_SHORT).show()
////        } else {
////            // We got a QR Code! Send it to the OfflineVerifier
////            // Make sure OfflineVerifier.kt exists!
////            val isValid = OfflineVerifier.verify(result.contents)
////
////            if (isValid) {
////                Toast.makeText(this, "✅ ACCESS GRANTED: Valid Ranger ID", Toast.LENGTH_LONG).show()
////            } else {
////                Toast.makeText(this, "❌ ACCESS DENIED: Fake or Expired ID", Toast.LENGTH_LONG).show()
////            }
////        }
////    }
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        setContent {
////            RangerVaultApp()
////        }
////    }
////}
////
////@Composable
////fun RangerVaultApp() {
////    // UI State
////    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
////    var statusText by remember { mutableStateOf("Ready to authenticate") }
////    val scope = rememberCoroutineScope()
////
////    // We need Context to trigger the Scanner from the Button
////    val context = LocalContext.current
////
////    Column(
////        modifier = Modifier.fillMaxSize().padding(24.dp),
////        horizontalAlignment = Alignment.CenterHorizontally,
////        verticalArrangement = Arrangement.Center
////    ) {
////        Text(text = "RangerVault Identity", style = MaterialTheme.typography.headlineMedium)
////
////        Spacer(modifier = Modifier.height(32.dp))
////
////        // Show QR Code if it exists
////        if (qrBitmap != null) {
////            Image(
////                bitmap = qrBitmap!!.asImageBitmap(),
////                contentDescription = "Identity QR",
////                modifier = Modifier.size(250.dp)
////            )
////            Spacer(modifier = Modifier.height(16.dp))
////            Text("Valid for 30 seconds", color = MaterialTheme.colorScheme.primary)
////        } else {
////            // Placeholder box
////            Surface(
////                modifier = Modifier.size(250.dp),
////                color = MaterialTheme.colorScheme.surfaceVariant
////            ) {
////                Box(contentAlignment = Alignment.Center) {
////                    Text("No Active Pass")
////                }
////            }
////        }
////
////        Spacer(modifier = Modifier.height(32.dp))
////        Text(text = statusText)
////        Spacer(modifier = Modifier.height(16.dp))
////
////        // BUTTON 1: Generate ID
////        Button(onClick = {
////            scope.launch {
////                statusText = "Contacting HQ..."
////                try {
////                    // Call the Server
////                    val response = api.getIdentity(IdentityRequest("Ranger_Red", "Leader"))
////
////                    // Combine Data
////                    val finalString = "${response.payload}##${response.signature}"
////
////                    // Generate QR
////                    qrBitmap = generateQRCode(finalString)
////                    statusText = "Identity Secure & Active"
////
////                } catch (e: Exception) {
////                    e.printStackTrace()
////                    statusText = "Error: ${e.localizedMessage}"
////                }
////            }
////        }) {
////            Text("Generate Secure ID")
////        }
////
////        Spacer(modifier = Modifier.height(16.dp))
////
////        // --- NEW BUTTON 2: Verify ID (Scanner) ---
////        Button(
////            onClick = {
////                val options = ScanOptions()
////                options.setPrompt("Scan Ranger ID to Verify")
////                options.setBeepEnabled(true)
////                options.setOrientationLocked(false)
////
////                // Launch the scanner defined in MainActivity
////                (context as MainActivity).scanLauncher.launch(options)
////            },
////            // Use the full name "androidx.compose.ui.graphics.Color"
////            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red)
////        ) {
////            Text("VERIFY ID (Scanner Mode)")
////        }
////    }
////}
////
////// Helper Function to draw QR
////fun generateQRCode(content: String): Bitmap? {
////    val writer = QRCodeWriter()
////    return try {
////        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
////        val width = bitMatrix.width
////        val height = bitMatrix.height
////        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
////        for (x in 0 until width) {
////            for (y in 0 until height) {
////                bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
////            }
////        }
////        bmp
////    } catch (e: Exception) {
////        null
////    }
////}
////updatedcode
//
//
////final
//package com.example.rangervault
//
//import android.Manifest
//import android.content.Context
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.graphics.Bitmap
//import android.graphics.Color as AndroidColor
//import android.media.MediaPlayer
//import android.net.Uri
//import android.os.Build
//import android.os.Bundle
//import android.os.VibrationEffect
//import android.os.Vibrator
//import android.provider.Settings
//import android.widget.Toast
//import androidx.activity.compose.BackHandler
//import androidx.activity.compose.setContent
//import androidx.appcompat.app.AppCompatActivity
//import androidx.biometric.BiometricManager
//import androidx.biometric.BiometricPrompt
//import androidx.compose.animation.core.*
//import androidx.compose.foundation.*
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.filled.ExitToApp
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import androidx.navigation.compose.NavHost
//import androidx.navigation.compose.composable
//import androidx.navigation.compose.currentBackStackEntryAsState
//import androidx.navigation.compose.rememberNavController
//import com.google.android.gms.location.LocationServices
//import com.google.gson.Gson
//import com.google.gson.reflect.TypeToken
//import com.google.zxing.BarcodeFormat
//import com.google.zxing.qrcode.QRCodeWriter
//import com.journeyapps.barcodescanner.CaptureActivity
//import com.journeyapps.barcodescanner.ScanContract
//import com.journeyapps.barcodescanner.ScanOptions
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import java.text.SimpleDateFormat
//import java.util.Date
//import java.util.Locale
//
//// --- CUSTOM ACTIVITY TO FORCE PORTRAIT MODE ---
//class PortraitCaptureActivity : CaptureActivity()
//
//// --- DATA CLASS ---
//data class GeoLogEntry(
//    val user: String,
//    val action: String,
//    val lat: Double,
//    val lng: Double,
//    val time: String,
//    val wasSuccess: Boolean,
//    val deviceId: String
//)
//
////class MainActivity : AppCompatActivity() {
////
////    // --- STATE ---
////    var isAuthenticated by mutableStateOf(false)
////    var isLoggedIn by mutableStateOf(false)
////    var currentUserName by mutableStateOf("")
////    var currentUserRole by mutableStateOf("Ranger")
////
////    // --- DATA ---
////    val scanHistory = mutableStateListOf<Pair<String, Boolean>>()
////    val globalGeoLogs = mutableStateListOf<GeoLogEntry>()
////
////    // --- ANALYTICS ---
////    var totalScans by mutableStateOf(0)
////    var successfulScans by mutableStateOf(0)
////    var failedScans by mutableStateOf(0)
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        loadLogsFromStorage()
////        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
////        setContent {
////            RangerVaultTheme {
////                if (!isAuthenticated) LockScreen({ showBiometricPrompt() }, { isAuthenticated = true })
////                else if (!isLoggedIn) LoginScreen { name, role -> currentUserName = name; currentUserRole = role; isLoggedIn = true }
////                else MainScreen(this)
////            }
////        }
////    }
//
//class MainActivity : AppCompatActivity() {
//
//    // --- STATE ---
//    var isAuthenticated by mutableStateOf(false)
//    var isLoggedIn by mutableStateOf(false)
//    var currentUserName by mutableStateOf("")
//    var currentUserRole by mutableStateOf("Ranger")
//
//    // --- NEW VARIABLES FOR RESULT SCREEN ---
//    var lastScanSuccess by mutableStateOf(false)
//    var lastScannedUser by mutableStateOf("")
//    var pendingNavigation by mutableStateOf<String?>(null) // Triggers navigation
//
//    // --- DATA ---
//    val scanHistory = mutableStateListOf<Pair<String, Boolean>>()
//    val globalGeoLogs = mutableStateListOf<GeoLogEntry>()
//
//    // --- ANALYTICS ---
//    var totalScans by mutableStateOf(0)
//    var successfulScans by mutableStateOf(0)
//    var failedScans by mutableStateOf(0)
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        loadLogsFromStorage()
//        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
//        setContent {
//            RangerVaultTheme {
//                if (!isAuthenticated) LockScreen({ showBiometricPrompt() }, { isAuthenticated = true })
//                else if (!isLoggedIn) LoginScreen { name, role -> currentUserName = name; currentUserRole = role; isLoggedIn = true }
//                else MainScreen(this)
//            }
//        }
//    }
//
//    // --- SAVE / LOAD ---
//    fun saveLogsToStorage() {
//        val sharedPref = getSharedPreferences("RangerData", Context.MODE_PRIVATE)
//        val editor = sharedPref.edit()
//        val gson = Gson()
//        editor.putString("logs_key", gson.toJson(globalGeoLogs))
//        editor.putInt("success_count", successfulScans)
//        editor.putInt("fail_count", failedScans)
//        editor.putInt("total_count", totalScans)
//        editor.apply()
//    }
//
//    fun loadLogsFromStorage() {
//        val sharedPref = getSharedPreferences("RangerData", Context.MODE_PRIVATE)
//        val gson = Gson()
//        successfulScans = sharedPref.getInt("success_count", 0)
//        failedScans = sharedPref.getInt("fail_count", 0)
//        totalScans = sharedPref.getInt("total_count", 0)
//
//        val json = sharedPref.getString("logs_key", null)
//        if (json != null) {
//            val type = object : TypeToken<List<GeoLogEntry>>() {}.type
//            val savedList: List<GeoLogEntry> = gson.fromJson(json, type)
//            globalGeoLogs.clear()
//            globalGeoLogs.addAll(savedList)
//        }
//    }
//
//    fun clearAllData() {
//        val sharedPref = getSharedPreferences("RangerData", Context.MODE_PRIVATE)
//        sharedPref.edit().clear().apply()
//        globalGeoLogs.clear()
//        scanHistory.clear()
//        successfulScans = 0; failedScans = 0; totalScans = 0
//        Toast.makeText(this, "SYSTEM PURGED", Toast.LENGTH_SHORT).show()
//    }
//
//    fun logout() { isLoggedIn = false; currentUserName = "" }
//
//    // --- LOGIC ---
//    fun getLocationAndLog(userId: String, actionType: String, isSuccess: Boolean) {
//        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
//        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "Offline-Device"
//        val timeNow = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
//
//        fun saveAndShow(lat: Double, lng: Double) {
//            runOnUiThread {
//                val entry = GeoLogEntry(userId, actionType, lat, lng, timeNow, isSuccess, deviceId)
//                globalGeoLogs.add(0, entry)
//                saveLogsToStorage()
//            }
//        }
//
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
//                saveAndShow(loc?.latitude ?: 0.0, loc?.longitude ?: 0.0)
//                sendToBackend(userId, actionType, loc?.latitude ?: 0.0, loc?.longitude ?: 0.0, deviceId)
//            }
//        } else {
//            saveAndShow(0.0, 0.0)
//            sendToBackend(userId, actionType, 0.0, 0.0, deviceId)
//        }
//    }
//
//    private fun sendToBackend(userId: String, status: String, lat: Double, lng: Double, deviceId: String) {
//        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
//            try { NetworkClient.api.sendLog(LogRequest(userId, status, lat, lng, deviceId)) } catch (e: Exception) { }
//        }
//    }
//
//    // --- SCANNER LAUNCHER ---
////    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
////        if (result.contents != null) {
////            val isValid = OfflineVerifier.verify(result.contents)
////            playSound(isValid); vibratePhone(isValid)
////            val payload = result.contents.split("##")[0]
////            val scannedUserId = try { payload.split("|")[0] } catch (e: Exception) { "Unknown" }
////
////            scanHistory.add(0, scannedUserId to isValid)
////            totalScans++; if(isValid) successfulScans++ else failedScans++
////            getLocationAndLog(scannedUserId, "SCANNED", isValid)
////        }
////    }
//
//    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
//        if (result.contents != null) {
//            val isValid = OfflineVerifier.verify(result.contents)
//            playSound(isValid); vibratePhone(isValid)
//
//            val payload = result.contents.split("##")[0]
//            val scannedUserId = try { payload.split("|")[0] } catch (e: Exception) { "Unknown" }
//
//            // 1. SAVE RESULT
//            lastScanSuccess = isValid
//            lastScannedUser = scannedUserId
//
//            // 2. TRIGGER NAVIGATION TO RESULT SCREEN
//            pendingNavigation = "result_screen"
//
//            scanHistory.add(0, scannedUserId to isValid)
//            totalScans++; if(isValid) successfulScans++ else failedScans++
//            getLocationAndLog(scannedUserId, "SCANNED", isValid)
//        }
//    }
//
//    fun showBiometricPrompt() {
//        val biometricManager = BiometricManager.from(this)
//        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) { isAuthenticated = true; return }
//        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
//            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { isAuthenticated = true; playSound(true) }
//        })
//        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Black Ranger Ops").setNegativeButtonText("Cancel").build())
//    }
//
//    fun playSound(isSuccess: Boolean) { try { MediaPlayer.create(this, if (isSuccess) R.raw.scan_success else R.raw.scan_fail).start() } catch (e: Exception) {} }
//    fun vibratePhone(isSuccess: Boolean) { (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(if(isSuccess) 100 else 500, VibrationEffect.DEFAULT_AMPLITUDE)) }
//}
//
//// --- THEME ---
//@Composable
//fun RangerVaultTheme(content: @Composable () -> Unit) {
//    MaterialTheme(colorScheme = darkColorScheme(
//        primary = Color(0xFFFFD700),
//        onPrimary = Color.Black,
//        background = Color(0xFF000000),
//        surface = Color(0xFF121212)
//    )) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
//}
//
//// --- SCREENS ---
//@Composable
//fun LockScreen(onUnlock: () -> Unit, onBypass: () -> Unit) {
//    LaunchedEffect(Unit) { delay(800); onUnlock() }
//    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
//        Column(horizontalAlignment = Alignment.CenterHorizontally) {
//            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
//            Spacer(Modifier.height(16.dp))
//            Text("BLACK RANGER OPS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
//            Spacer(Modifier.height(32.dp))
//            Button(onClick = onUnlock, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
//                Text("INITIATE PROTOCOL", color = Color.Black, fontWeight = FontWeight.Bold)
//            }
//            TextButton(onClick = onBypass) { Text("Override", color = Color.DarkGray) }
//        }
//    }
//}
//
//@Composable
//fun LoginScreen(onLogin: (String, String) -> Unit) {
//    var name by remember { mutableStateOf("") }
//    var role by remember { mutableStateOf("Ranger") }
//    BackHandler(enabled = true) { }
//
//    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
//        Text("IDENTITY LOGIN", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
//        Spacer(Modifier.height(24.dp))
//        OutlinedTextField(
//            value = name, onValueChange = { name = it },
//            label = { Text("Operative Name") },
//            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, focusedLabelColor = MaterialTheme.colorScheme.primary),
//            modifier = Modifier.fillMaxWidth()
//        )
//        Spacer(Modifier.height(16.dp))
//        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
//            FilterChip(selected = role == "Ranger", onClick = { role = "Ranger" }, label = { Text("Ranger") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary))
//            FilterChip(selected = role == "Commander", onClick = { role = "Commander" }, label = { Text("Commander") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary))
//        }
//        Spacer(Modifier.height(24.dp))
//        Button(
//            onClick = { if (name.isNotEmpty()) onLogin(name, role) },
//            modifier = Modifier.fillMaxWidth(),
//            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
//        ) { Text("ACCESS SYSTEM", color = Color.Black, fontWeight = FontWeight.Bold) }
//    }
//}
//
//
//
////@OptIn(ExperimentalMaterial3Api::class)
////@Composable
////fun MainScreen(activity: MainActivity) {
////    val navController = rememberNavController()
////    val navBackStackEntry by navController.currentBackStackEntryAsState()
////    val currentRoute = navBackStackEntry?.destination?.route
////    val startDest = if (activity.currentUserRole == "Commander") "admin_dashboard" else "id_screen"
////
////    Scaffold(
////        topBar = {
////            TopAppBar(
////                title = { Text(activity.currentUserName, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
////                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
////                actions = { IconButton(onClick = { activity.logout() }) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = Color.Gray) } }
////            )
////        },
////        bottomBar = {
////            NavigationBar(containerColor = Color.Black) {
////                if (activity.currentUserRole == "Commander") NavigationBarItem(selected = currentRoute=="admin_dashboard", onClick={navController.navigate("admin_dashboard")}, icon={Icon(Icons.Default.Dashboard,null)}, label={Text("Admin")}, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary))
////                NavigationBarItem(selected = currentRoute=="id_screen", onClick={navController.navigate("id_screen")}, icon={Icon(Icons.Default.Fingerprint,null)}, label={Text("ID")}, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary))
////                NavigationBarItem(selected = currentRoute=="scan_screen", onClick={navController.navigate("scan_screen")}, icon={Icon(Icons.Default.QrCodeScanner,null)}, label={Text("Scan")}, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary))
////            }
////        }
////    ) { p ->
////        NavHost(navController, startDest, Modifier.padding(p)) {
////            composable("admin_dashboard") { AdminDashboardScreen(activity) }
////            composable("id_screen") { IdGeneratorScreen(activity) }
////            composable("scan_screen") { ScannerScreen(activity) }
////        }
////    }
////}
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun MainScreen(activity: MainActivity) {
//    val navController = rememberNavController()
//    val navBackStackEntry by navController.currentBackStackEntryAsState()
//    val currentRoute = navBackStackEntry?.destination?.route
//    val startDest = if (activity.currentUserRole == "Commander") "admin_dashboard" else "id_screen"
//
//    // --- LISTENER FOR SCAN RESULT NAVIGATION ---
//    LaunchedEffect(activity.pendingNavigation) {
//        activity.pendingNavigation?.let {
//            navController.navigate(it)
//            activity.pendingNavigation = null // Reset trigger
//        }
//    }
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text(activity.currentUserName, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
//                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
//                actions = { IconButton(onClick = { activity.logout() }) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = Color.Gray) } }
//            )
//        },
//        bottomBar = {
//            NavigationBar(containerColor = Color.Black) {
//                if (activity.currentUserRole == "Commander") NavigationBarItem(selected = currentRoute=="admin_dashboard", onClick={navController.navigate("admin_dashboard")}, icon={Icon(Icons.Default.Dashboard,null)}, label={Text("Admin")}, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary))
//                NavigationBarItem(selected = currentRoute=="id_screen", onClick={navController.navigate("id_screen")}, icon={Icon(Icons.Default.Fingerprint,null)}, label={Text("ID")}, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary))
//                NavigationBarItem(selected = currentRoute=="scan_screen", onClick={navController.navigate("scan_screen")}, icon={Icon(Icons.Default.QrCodeScanner,null)}, label={Text("Scan")}, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary))
//            }
//        }
//    ) { p ->
//        NavHost(navController, startDest, Modifier.padding(p)) {
//            composable("admin_dashboard") { AdminDashboardScreen(activity) }
//            composable("id_screen") { IdGeneratorScreen(activity) }
//            composable("scan_screen") { ScannerScreen(activity) }
//            // 👇 NEW SCREEN ROUTE 👇
//            composable("result_screen") {
//                ResultScreen(
//                    isSuccess = activity.lastScanSuccess,
//                    username = activity.lastScannedUser,
//                    onBack = { navController.popBackStack() }
//                )
//            }
//        }
//    }
//}
//
//@Composable
//fun AdminDashboardScreen(activity: MainActivity) {
//    val context = LocalContext.current
//    BackHandler { activity.logout() }
//
//    Column(Modifier.fillMaxSize().padding(16.dp)) {
//        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
//            Text("COMMANDER OPS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
//            Button(onClick = { activity.clearAllData() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF330000)), contentPadding = PaddingValues(horizontal=8.dp)) {
//                Icon(Icons.Default.DeleteForever, null, tint = Color.Red); Text("PURGE", color = Color.Red)
//            }
//        }
//
//        Spacer(Modifier.height(16.dp))
//
//        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)), modifier = Modifier.fillMaxWidth().padding(bottom=16.dp)) {
//            Column(Modifier.padding(16.dp)) {
//                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
//                    Text("Verification Status", color = Color.White, fontWeight = FontWeight.Bold)
//                    Text("Total: ${activity.totalScans}", color = Color.Gray)
//                }
//                Spacer(Modifier.height(12.dp))
//                Row(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(4.dp)).background(Color.DarkGray)) {
//                    if (activity.totalScans > 0) {
//                        val successWeight = activity.successfulScans.toFloat() / activity.totalScans.toFloat()
//                        val failWeight = activity.failedScans.toFloat() / activity.totalScans.toFloat()
//                        if (successWeight > 0) Box(Modifier.weight(successWeight).fillMaxHeight().background(Color(0xFF00C853)))
//                        if (failWeight > 0) Box(Modifier.weight(failWeight).fillMaxHeight().background(Color(0xFFD50000)))
//                    }
//                }
//                Spacer(Modifier.height(8.dp))
//                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
//                    Text("✔ ${activity.successfulScans} Granted", color = Color(0xFF00C853), fontSize = 12.sp)
//                    Text("✖ ${activity.failedScans} Denied", color = Color(0xFFD50000), fontSize = 12.sp)
//                }
//            }
//        }
//
//        Text("FIELD LOGS (OFFLINE)", color = Color.White, fontWeight = FontWeight.Bold)
//        Spacer(Modifier.height(8.dp))
//
//        LazyColumn {
//            items(activity.globalGeoLogs.size) { i ->
//                val log = activity.globalGeoLogs[i]
//                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), modifier = Modifier.fillMaxWidth().padding(vertical=4.dp).clickable {
//                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${log.lat},${log.lng}?q=${log.lat},${log.lng}(${log.user})")).setPackage("com.google.android.apps.maps")) } catch(e: Exception){}
//                }) {
//                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
//                        Icon(if(log.action=="CREATED") Icons.Default.AddCircle else Icons.Default.QrCodeScanner, null, tint = if(log.action=="CREATED") Color.Cyan else (if(log.wasSuccess) MaterialTheme.colorScheme.primary else Color.Red), modifier = Modifier.size(32.dp))
//                        Spacer(Modifier.width(12.dp))
//                        Column {
//                            Text("${log.user} [${log.action}]", color = Color.White, fontWeight = FontWeight.Bold)
//                            Text("Lat: ${log.lat}, Lng: ${log.lng}", color = Color.LightGray, fontSize = 11.sp)
//                            Text("${log.time} | ID: ${log.deviceId.take(4)}", color = Color.DarkGray, fontSize = 10.sp)
//                        }
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun IdGeneratorScreen(activity: MainActivity) {
//    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
//    var timeLeft by remember { mutableStateOf(0) }
//    val scope = rememberCoroutineScope()
//    val context = LocalContext.current
//
//    LaunchedEffect(qrBitmap) { if (qrBitmap != null) { timeLeft = 30; while (timeLeft > 0) { delay(1000L); timeLeft-- }; qrBitmap = null } }
//
//    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
//        if (qrBitmap != null) {
//            Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)))
//            Text("${timeLeft}s", color = MaterialTheme.colorScheme.primary, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top=16.dp))
//            Text("TOKEN EXPIRING", color = Color.Gray, fontSize = 12.sp)
//        } else {
//            Icon(Icons.Default.Shield, null, Modifier.size(80.dp), tint = Color.DarkGray)
//            Spacer(Modifier.height(16.dp))
//            Text(activity.currentUserName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
//            Text("Security Level: ${activity.currentUserRole}", color = MaterialTheme.colorScheme.primary)
//        }
//        Spacer(Modifier.height(32.dp))
//        Button(
//            onClick = {
//                scope.launch {
//                    try {
//                        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
//                        val response = NetworkClient.api.getIdentity(IdentityRequest(activity.currentUserName, activity.currentUserRole, deviceId))
//                        qrBitmap = generateQRCode("${response.payload}##${response.signature}")
//                        activity.getLocationAndLog(activity.currentUserName, "CREATED", true)
//                    } catch (e: Exception) { Toast.makeText(context, "Network Link Failed", Toast.LENGTH_SHORT).show() }
//                }
//            },
//            modifier = Modifier.fillMaxWidth().height(50.dp),
//            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
//        ) { Text("GENERATE SECURE TOKEN", color = Color.Black, fontWeight = FontWeight.Bold) }
//    }
//}
//
//@Composable
//fun ScannerScreen(activity: MainActivity) {
//    // --- VERTICAL ANIMATION ---
//    val infiniteTransition = rememberInfiniteTransition()
//    val offsetY by infiniteTransition.animateFloat(
//        initialValue = 0f,
//        targetValue = 200f,
//        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse)
//    )
//
//    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
//        Box(Modifier.size(250.dp).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)).clickable {
//            // *** IMPORTANT: USES CUSTOM PORTRAIT ACTIVITY ***
//            val options = ScanOptions()
//            options.setCaptureActivity(PortraitCaptureActivity::class.java)
//            options.setOrientationLocked(true) // LOCKS TO PORTRAIT
//            options.setBeepEnabled(false)
//            activity.scanLauncher.launch(options)
//        }, contentAlignment = Alignment.Center) {
//
//            // --- ANIMATED SCAN LINE (UP AND DOWN) ---
//            Box(Modifier.fillMaxWidth().height(2.dp).offset(y = offsetY.dp).background(Color.Red))
//
//            Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                Icon(Icons.Default.QrCodeScanner, null, tint = Color.Gray, modifier = Modifier.size(50.dp))
//                Text("Tap to Scan", color = Color.Gray)
//            }
//        }
//    }
//}
//
//fun generateQRCode(content: String): Bitmap? {
//    val writer = QRCodeWriter()
//    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
//    val width = bitMatrix.width; val height = bitMatrix.height
//    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
//    for (x in 0 until width) { for (y in 0 until height) { bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE) } }
//    return bmp
//}
//
//@Composable
//fun ResultScreen(isSuccess: Boolean, username: String, onBack: () -> Unit) {
//    val bgColor = if (isSuccess) Color(0xFF004D40) else Color(0xFF4A0000) // Dark Green vs Dark Red
//    val iconColor = if (isSuccess) Color(0xFF00E676) else Color(0xFFFF1744) // Bright Green vs Bright Red
//    val mainText = if (isSuccess) "ACCESS GRANTED" else "ACCESS DENIED"
//    val subText = if (isSuccess) "Identity Verified" else "Security Protocol Violation"
//    val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(bgColor)
//            .padding(32.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        Icon(icon, null, tint = iconColor, modifier = Modifier.size(120.dp))
//
//        Spacer(Modifier.height(32.dp))
//
//        Text(mainText, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
//        Text(subText, color = Color.LightGray, fontSize = 14.sp)
//
//        Spacer(Modifier.height(48.dp))
//
//        Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f))) {
//            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
//                Text("OPERATIVE ID", color = Color.Gray, fontSize = 12.sp)
//                Spacer(Modifier.height(4.dp))
//                Text(username, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
//            }
//        }
//
//        Spacer(Modifier.height(64.dp))
//
//        Button(
//            onClick = onBack,
//            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
//            modifier = Modifier.fillMaxWidth().height(50.dp)
//        ) {
//            Text("RETURN TO SCANNER", color = Color.Black, fontWeight = FontWeight.Bold)
//        }
//    }
//}
//
////AFTERR rOLE BASED LOGIN
////package com.example.rangervault
////
////import android.Manifest
////import android.content.Intent
////import android.content.pm.PackageManager
////import android.graphics.Bitmap
////import android.graphics.Color as AndroidColor
////import android.media.MediaPlayer
////import android.net.Uri
////import android.os.Build
////import android.os.Bundle
////import android.os.VibrationEffect
////import android.os.Vibrator
////import android.provider.Settings
////import android.widget.Toast
////import androidx.activity.compose.setContent
////import androidx.appcompat.app.AppCompatActivity
////import androidx.biometric.BiometricManager
////import androidx.biometric.BiometricPrompt
////import androidx.compose.animation.core.*
////import androidx.compose.foundation.*
////import androidx.compose.foundation.layout.*
////import androidx.compose.foundation.lazy.LazyColumn
////import androidx.compose.foundation.shape.RoundedCornerShape
////import androidx.compose.material.icons.Icons
////import androidx.compose.material.icons.filled.*
////import androidx.compose.material3.*
////import androidx.compose.runtime.*
////import androidx.compose.ui.Alignment
////import androidx.compose.ui.Modifier
////import androidx.compose.ui.draw.clip
////import androidx.compose.ui.graphics.Brush
////import androidx.compose.ui.graphics.Color
////import androidx.compose.ui.graphics.asImageBitmap
////import androidx.compose.ui.graphics.vector.ImageVector
////import androidx.compose.ui.platform.LocalContext
////import androidx.compose.ui.text.font.FontWeight
////import androidx.compose.ui.unit.dp
////import androidx.compose.ui.unit.sp
////import androidx.core.app.ActivityCompat
////import androidx.core.content.ContextCompat
////import androidx.navigation.compose.NavHost
////import androidx.navigation.compose.composable
////import androidx.navigation.compose.currentBackStackEntryAsState
////import androidx.navigation.compose.rememberNavController
////import com.google.android.gms.location.LocationServices
////import com.google.zxing.BarcodeFormat
////import com.google.zxing.qrcode.QRCodeWriter
////import com.journeyapps.barcodescanner.CaptureActivity
////import com.journeyapps.barcodescanner.ScanContract
////import com.journeyapps.barcodescanner.ScanOptions
////import kotlinx.coroutines.Dispatchers
////import kotlinx.coroutines.delay
////import kotlinx.coroutines.launch
////import java.text.SimpleDateFormat
////import java.util.Date
////import java.util.Locale
////
////// --- DATA CLASS FOR GEO LOGS ---
////data class GeoLogEntry(
////    val user: String,
////    val action: String, // "CREATED" or "SCANNED"
////    val lat: Double,
////    val lng: Double,
////    val time: String,
////    val wasSuccess: Boolean
////)
////
////class MainActivity : AppCompatActivity() {
////
////    // --- STATE ---
////    var isAuthenticated by mutableStateOf(false)
////    var isLoggedIn by mutableStateOf(false)
////    var currentUserName by mutableStateOf("")
////    var currentUserRole by mutableStateOf("Ranger")
////
////    // DATA STORES
////    val scanHistory = mutableStateListOf<Pair<String, Boolean>>()
////
////    // *** NEW: Global List to store Locations for Admin Dashboard ***
////    val globalGeoLogs = mutableStateListOf<GeoLogEntry>()
////
////    // ANALYTICS
////    var totalScans by mutableStateOf(0)
////    var successfulScans by mutableStateOf(0)
////    var failedScans by mutableStateOf(0)
////
////    // --- GEO-LOCATION LOGIC ---
////    // Now takes 'isGeneration' to distinguish between Creating and Scanning
////    fun getLocationAndLog(userId: String, actionType: String, isSuccess: Boolean) {
////        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
////        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
////        val timeNow = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
////
////        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
////            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
////                val lat = location?.latitude ?: 0.0
////                val lng = location?.longitude ?: 0.0
////
////                // 1. Add to Local List (For Admin Dashboard)
////                globalGeoLogs.add(0, GeoLogEntry(userId, actionType, lat, lng, timeNow, isSuccess))
////
////                // 2. Send to Server (Backend)
////                sendToBackend(userId, actionType, lat, lng, deviceId)
////            }
////        } else {
////            // Permission missing
////            globalGeoLogs.add(0, GeoLogEntry(userId, actionType, 0.0, 0.0, timeNow, isSuccess))
////            sendToBackend(userId, actionType, 0.0, 0.0, deviceId)
////        }
////    }
////
////    private fun sendToBackend(userId: String, status: String, lat: Double, lng: Double, deviceId: String) {
////        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
////            try {
////                NetworkClient.api.sendLog(LogRequest(userId, status, lat, lng, deviceId))
////            } catch (e: Exception) { e.printStackTrace() }
////        }
////    }
////
////    // --- SCANNER LAUNCHER ---
////    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
////        if (result.contents != null) {
////            val isValid = OfflineVerifier.verify(result.contents)
////            playSound(isValid)
////            vibratePhone(isValid)
////
////            val payload = result.contents.split("##")[0]
////            val scannedUserId = try { payload.split("|")[0] } catch (e: Exception) { "Unknown" }
////
////            scanHistory.add(0, scannedUserId to isValid)
////            totalScans++
////            if(isValid) successfulScans++ else failedScans++
////
////            // LOG LOCATION: SCANNED
////            getLocationAndLog(scannedUserId, "SCANNED", isValid)
////        }
////    }
////
////    // --- BIOMETRICS ---
////    fun showBiometricPrompt() {
////        val biometricManager = BiometricManager.from(this)
////        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
////            isAuthenticated = true
////            return
////        }
////        try {
////            val executor = ContextCompat.getMainExecutor(this)
////            val biometricPrompt = BiometricPrompt(this, executor,
////                object : BiometricPrompt.AuthenticationCallback() {
////                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
////                        isAuthenticated = true
////                        playSound(true)
////                    }
////                })
////            biometricPrompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("RangerVault").setNegativeButtonText("Cancel").build())
////        } catch (e: Exception) { isAuthenticated = true }
////    }
////
////    fun playSound(isSuccess: Boolean) {
////        try {
////            val mp = MediaPlayer.create(this, if (isSuccess) R.raw.scan_success else R.raw.scan_fail)
////            mp.start()
////            mp.setOnCompletionListener { it.release() }
////        } catch (e: Exception) {}
////    }
////
////    fun vibratePhone(isSuccess: Boolean) {
////        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
////        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(if(isSuccess) 100 else 500, VibrationEffect.DEFAULT_AMPLITUDE))
////        else v.vibrate(200)
////    }
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
////        setContent {
////            RangerVaultTheme {
////                if (!isAuthenticated) LockScreen({ showBiometricPrompt() }, { isAuthenticated = true })
////                else if (!isLoggedIn) LoginScreen { name, role -> currentUserName = name; currentUserRole = role; isLoggedIn = true }
////                else MainScreen(this)
////            }
////        }
////    }
////}
////
////// --- THEME & SCREENS ---
////@Composable
////fun RangerVaultTheme(content: @Composable () -> Unit) {
////    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE53935), onPrimary = Color.White, background = Color(0xFF000000), surface = Color(0xFF121212))) {
////        Surface(color = MaterialTheme.colorScheme.background) { content() }
////    }
////}
////
////@Composable
////fun LockScreen(onUnlock: () -> Unit, onBypass: () -> Unit) {
////    LaunchedEffect(Unit) { delay(800); onUnlock() }
////    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
////        Column(horizontalAlignment = Alignment.CenterHorizontally) {
////            Icon(Icons.Default.Security, null, tint = Color.Red, modifier = Modifier.size(80.dp))
////            Spacer(Modifier.height(16.dp))
////            Button(onClick = onUnlock) { Text("AUTHENTICATE") }
////            TextButton(onClick = onBypass) { Text("Dev Override", color = Color.Gray) }
////        }
////    }
////}
////
////@Composable
////fun LoginScreen(onLogin: (String, String) -> Unit) {
////    var name by remember { mutableStateOf("") }
////    var role by remember { mutableStateOf("Ranger") }
////    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
////        Text("IDENTITY LOGIN", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
////        Spacer(Modifier.height(24.dp))
////        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
////        Spacer(Modifier.height(16.dp))
////        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
////            FilterChip(selected = role == "Ranger", onClick = { role = "Ranger" }, label = { Text("Ranger") })
////            FilterChip(selected = role == "Commander", onClick = { role = "Commander" }, label = { Text("Commander") })
////        }
////        Spacer(Modifier.height(24.dp))
////        Button(onClick = { if (name.isNotEmpty()) onLogin(name, role) }, modifier = Modifier.fillMaxWidth()) { Text("ACCESS TERMINAL") }
////    }
////}
////
////@OptIn(ExperimentalMaterial3Api::class)
////@Composable
////fun MainScreen(activity: MainActivity) {
////    val navController = rememberNavController()
////    val navBackStackEntry by navController.currentBackStackEntryAsState()
////    val currentRoute = navBackStackEntry?.destination?.route
////    val startDest = if (activity.currentUserRole == "Commander") "admin_dashboard" else "id_screen"
////
////    Scaffold(
////        bottomBar = {
////            NavigationBar(containerColor = Color.Black) {
////                if (activity.currentUserRole == "Commander") NavigationBarItem(selected = currentRoute=="admin_dashboard", onClick={navController.navigate("admin_dashboard")}, icon={Icon(Icons.Default.Dashboard,null)}, label={Text("Admin")})
////                NavigationBarItem(selected = currentRoute=="id_screen", onClick={navController.navigate("id_screen")}, icon={Icon(Icons.Default.Fingerprint,null)}, label={Text("ID")})
////                NavigationBarItem(selected = currentRoute=="scan_screen", onClick={navController.navigate("scan_screen")}, icon={Icon(Icons.Default.QrCodeScanner,null)}, label={Text("Scan")})
////            }
////        }
////    ) { p ->
////        NavHost(navController, startDest, Modifier.padding(p)) {
////            composable("admin_dashboard") { AdminDashboardScreen(activity) }
////            composable("id_screen") { IdGeneratorScreen(activity) }
////            composable("scan_screen") { ScannerScreen(activity) }
////        }
////    }
////}
////
////// --- UPDATED ADMIN DASHBOARD WITH MAP ---
////@Composable
////fun AdminDashboardScreen(activity: MainActivity) {
////    val context = LocalContext.current
////
////    Column(Modifier.fillMaxSize().padding(16.dp)) {
////        Text("COMMANDER DASHBOARD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
////        Text("Live Geo-Tracking", color = Color.Gray, fontSize = 14.sp)
////        Spacer(Modifier.height(16.dp))
////
////        // ANALYTICS CARD
////        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)), modifier = Modifier.fillMaxWidth().height(100.dp)) {
////            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
////                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${activity.successfulScans}", color = Color.Green, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Granted", color = Color.Gray) }
////                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${activity.failedScans}", color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Denied", color = Color.Gray) }
////            }
////        }
////
////        Spacer(Modifier.height(16.dp))
////        Text("GEO-LOGS (Tap to View Map)", color = Color.White, fontWeight = FontWeight.Bold)
////        Spacer(Modifier.height(8.dp))
////
////        // GEO LOGS LIST
////        LazyColumn {
////            items(activity.globalGeoLogs.size) { i ->
////                val log = activity.globalGeoLogs[i]
////                Card(
////                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
////                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
////                        // OPEN GOOGLE MAPS INTENT
////                        val uri = "geo:${log.lat},${log.lng}?q=${log.lat},${log.lng}(${log.user})"
////                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
////                        intent.setPackage("com.google.android.apps.maps")
////                        try { context.startActivity(intent) } catch (e: Exception) {
////                            Toast.makeText(context, "Maps app not found", Toast.LENGTH_SHORT).show()
////                        }
////                    }
////                ) {
////                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
////                        // Icon based on Action
////                        val icon = if(log.action == "CREATED") Icons.Default.AddCircle else Icons.Default.QrCodeScanner
////                        val color = if(log.action == "CREATED") Color.Cyan else (if(log.wasSuccess) Color.Green else Color.Red)
////
////                        Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
////                        Spacer(Modifier.width(12.dp))
////                        Column {
////                            Text("${log.user} - ${log.action}", color = Color.White, fontWeight = FontWeight.Bold)
////                            Text("Lat: ${log.lat}, Lng: ${log.lng}", color = Color.Gray, fontSize = 12.sp)
////                            Text(log.time, color = Color.DarkGray, fontSize = 12.sp)
////                        }
////                        Spacer(Modifier.weight(1f))
////                        Icon(Icons.Default.Map, null, tint = Color.Gray)
////                    }
////                }
////            }
////        }
////    }
////}
////
////@Composable
////fun IdGeneratorScreen(activity: MainActivity) {
////    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
////    var timeLeft by remember { mutableStateOf(0) }
////    val scope = rememberCoroutineScope()
////    val context = LocalContext.current
////
////    LaunchedEffect(qrBitmap) {
////        if (qrBitmap != null) {
////            timeLeft = 30
////            while (timeLeft > 0) { delay(1000L); timeLeft-- }
////            qrBitmap = null
////        }
////    }
////
////    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
////        if (qrBitmap != null) {
////            Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)))
////            Text("${timeLeft}s", color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top=16.dp))
////        } else {
////            Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = Color.Gray)
////            Text("Ready to Generate", color = Color.Gray)
////        }
////        Spacer(Modifier.height(32.dp))
////        Button(
////            onClick = {
////                scope.launch {
////                    try {
////                        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
////                        val response = NetworkClient.api.getIdentity(IdentityRequest(activity.currentUserName, activity.currentUserRole, deviceId))
////                        qrBitmap = generateQRCode("${response.payload}##${response.signature}")
////
////                        // *** LOG LOCATION: CREATED ***
////                        activity.getLocationAndLog(activity.currentUserName, "CREATED", true)
////
////                    } catch (e: Exception) { Toast.makeText(context, "Network Error", Toast.LENGTH_SHORT).show() }
////                }
////            },
////            modifier = Modifier.fillMaxWidth().height(50.dp)
////        ) { Text("GENERATE PASS") }
////    }
////}
////
////@Composable
////fun ScannerScreen(activity: MainActivity) {
////    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
////        Box(Modifier.size(250.dp).border(2.dp, Color.Red, RoundedCornerShape(12.dp)).clickable {
////            val options = ScanOptions()
////            options.setCaptureActivity(CaptureActivity::class.java)
////            options.setBeepEnabled(false)
////            activity.scanLauncher.launch(options)
////        }, contentAlignment = Alignment.Center) {
////            Column(horizontalAlignment = Alignment.CenterHorizontally) {
////                Icon(Icons.Default.QrCodeScanner, null, tint = Color.Gray, modifier = Modifier.size(50.dp))
////                Text("Tap to Scan", color = Color.Gray)
////            }
////        }
////    }
////}
//
////fun generateQRCode(content: String): Bitmap? {
////    val writer = QRCodeWriter()
////    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
////    val width = bitMatrix.width
////    val height = bitMatrix.height
////    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
////    for (x in 0 until width) {
////        for (y in 0 until height) {
////            bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
////        }
////    }
////    return bmp
////}
//
//
////
////package com.example.rangervault
////
////import android.graphics.Bitmap
////import android.graphics.Color as AndroidColor
////import android.os.Bundle
////import android.widget.Toast
////import androidx.activity.ComponentActivity
////import androidx.activity.compose.setContent
////import androidx.compose.foundation.Image
////import androidx.compose.foundation.background
////import androidx.compose.foundation.layout.*
////import androidx.compose.foundation.lazy.LazyColumn
////import androidx.compose.material.icons.Icons
////import androidx.compose.material.icons.filled.*
////import androidx.compose.material3.*
////import androidx.compose.runtime.*
////import androidx.compose.ui.Alignment
////import androidx.compose.ui.Modifier
////import androidx.compose.ui.graphics.asImageBitmap
////import androidx.compose.ui.text.font.FontWeight
////import androidx.compose.ui.unit.dp
////import androidx.navigation.compose.NavHost
////import androidx.navigation.compose.composable
////import androidx.navigation.compose.rememberNavController
////import com.google.zxing.BarcodeFormat
////import com.google.zxing.qrcode.QRCodeWriter
////import com.journeyapps.barcodescanner.ScanContract
////import com.journeyapps.barcodescanner.ScanOptions
////import kotlinx.coroutines.Dispatchers
////import kotlinx.coroutines.delay
////import kotlinx.coroutines.launch
////import java.text.SimpleDateFormat
////import java.util.Date
////import java.util.Locale
////
////class MainActivity : ComponentActivity() {
////
////    // --- STATE: History Lists ---
////    // 1. People you scanned
////    val scanHistory = mutableStateListOf<Pair<String, Boolean>>()
////    // 2. IDs you generated
////    val generationHistory = mutableStateListOf<Pair<String, String>>()
////
////    // --- SCANNER LAUNCHER ---
////    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
////        if (result.contents != null) {
////            val isValid = OfflineVerifier.verify(result.contents)
////
////            // Extract Name
////            val payload = result.contents.split("##")[0]
////            val userId = try { payload.split("|")[0] } catch (e: Exception) { "Unknown" }
////
////            // Update UI History
////            scanHistory.add(0, userId to isValid)
////
////            // Show Toast
////            val msg = if (isValid) "✅ ACCESS GRANTED" else "❌ ACCESS DENIED"
////            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
////
////            // Log to Server (Background)
////            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
////                try {
////                    NetworkClient.api.sendLog(LogRequest(userId, if (isValid) "GRANTED" else "DENIED"))
////                } catch (e: Exception) { e.printStackTrace() }
////            }
////        }
////    }
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        setContent {
////            RangerVaultTheme {
////                MainScreen(this)
////            }
////        }
////    }
////}
////
////// --- THEME ---
////@Composable
////fun RangerVaultTheme(content: @Composable () -> Unit) {
////    MaterialTheme(
////        colorScheme = darkColorScheme(
////            primary = androidx.compose.ui.graphics.Color(0xFFE53935), // Ranger Red
////            secondary = androidx.compose.ui.graphics.Color(0xFF1E88E5),
////            background = androidx.compose.ui.graphics.Color(0xFF121212),
////            surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E)
////        )
////    ) {
////        Surface(color = MaterialTheme.colorScheme.background) {
////            content()
////        }
////    }
////}
////
////// --- MAIN NAVIGATION ---
////@OptIn(ExperimentalMaterial3Api::class)
////@Composable
////fun MainScreen(activity: MainActivity) {
////    val navController = rememberNavController()
////
////    Scaffold(
////        bottomBar = {
////            NavigationBar(containerColor = androidx.compose.ui.graphics.Color(0xFF000000)) {
////                NavigationBarItem(
////                    selected = navController.currentDestination?.route == "id_screen",
////                    onClick = { navController.navigate("id_screen") },
////                    icon = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
////                    label = { Text("My ID") }
////                )
////                NavigationBarItem(
////                    selected = navController.currentDestination?.route == "scan_screen",
////                    onClick = { navController.navigate("scan_screen") },
////                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
////                    label = { Text("Scanner") }
////                )
////            }
////        }
////    ) { padding ->
////        NavHost(
////            navController = navController,
////            startDestination = "id_screen",
////            modifier = Modifier.padding(padding)
////        ) {
////            composable("id_screen") {
////                IdGeneratorScreen(activity.generationHistory)
////            }
////            composable("scan_screen") {
////                ScannerScreen(activity)
////            }
////        }
////    }
////}
////
////// --- SCREEN 1: GENERATOR ---
////@Composable
////fun IdGeneratorScreen(history: MutableList<Pair<String, String>>) {
////    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
////    var timeLeft by remember { mutableStateOf(0) }
////    var statusText by remember { mutableStateOf("Tap below to generate") }
////    val scope = rememberCoroutineScope()
////
////    // Timer Logic
////    LaunchedEffect(qrBitmap) {
////        if (qrBitmap != null) {
////            timeLeft = 30
////            while (timeLeft > 0) {
////                delay(1000L)
////                timeLeft--
////            }
////            qrBitmap = null
////            statusText = "ID Expired."
////        }
////    }
////
////    Column(
////        modifier = Modifier.fillMaxSize().padding(16.dp),
////        horizontalAlignment = Alignment.CenterHorizontally
////    ) {
////        // Upper Card
////        Card(
////            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
////            modifier = Modifier.fillMaxWidth().height(280.dp)
////        ) {
////            Column(
////                modifier = Modifier.fillMaxSize(),
////                horizontalAlignment = Alignment.CenterHorizontally,
////                verticalArrangement = Arrangement.Center
////            ) {
////                if (qrBitmap != null) {
////                    Image(
////                        bitmap = qrBitmap!!.asImageBitmap(),
////                        contentDescription = "QR",
////                        modifier = Modifier.size(180.dp)
////                    )
////                    Spacer(modifier = Modifier.height(10.dp))
////                    LinearProgressIndicator(progress = { timeLeft / 30f })
////                    Spacer(modifier = Modifier.height(5.dp))
////                    Text("Valid: ${timeLeft}s", color = androidx.compose.ui.graphics.Color.White)
////                } else {
////                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(60.dp), tint = androidx.compose.ui.graphics.Color.Gray)
////                    Text("Secure Vault Locked", color = androidx.compose.ui.graphics.Color.Gray)
////                }
////            }
////        }
////
////        Spacer(modifier = Modifier.height(16.dp))
////
////        // Generate Button
////        Button(
////            onClick = {
////                scope.launch {
////                    statusText = " contacting HQ..."
////                    try {
////                        val role = "Commander"
////                        val response = NetworkClient.api.getIdentity(IdentityRequest("Ranger_Red", role))
////                        val finalString = "${response.payload}##${response.signature}"
////                        qrBitmap = generateQRCode(finalString)
////                        statusText = "Identity Active"
////
////                        // Add to History
////                        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
////                        history.add(0, role to time)
////
////                        // Log to Server
////                        try { NetworkClient.api.sendLog(LogRequest("Ranger_Red", "GENERATED_ID")) } catch (e: Exception) {}
////
////                    } catch (e: Exception) {
////                        statusText = "Network Error"
////                        e.printStackTrace()
////                    }
////                }
////            },
////            modifier = Modifier.fillMaxWidth().height(50.dp),
////            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
////        ) {
////            Text("GENERATE SECURE PASS")
////        }
////
////        Text(statusText, modifier = Modifier.padding(8.dp), color = androidx.compose.ui.graphics.Color.Gray)
////
////        Spacer(modifier = Modifier.height(8.dp))
////        Divider(color = androidx.compose.ui.graphics.Color.DarkGray)
////        Spacer(modifier = Modifier.height(8.dp))
////
////        // History List
////        Text("Generation Log", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.LightGray, modifier = Modifier.align(Alignment.Start))
////
////        LazyColumn {
////            items(history.size) { i ->
////                val item = history[i]
////                HistoryItem(icon = Icons.Default.History, title = item.first, subtitle = "Time: ${item.second}", color = androidx.compose.ui.graphics.Color.White)
////            }
////        }
////    }
////}
////
////// --- SCREEN 2: SCANNER ---
////@Composable
////fun ScannerScreen(activity: MainActivity) {
////    val history = activity.scanHistory
////
////    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
////
////        // Scan Button
////        Card(
////            modifier = Modifier.fillMaxWidth().height(120.dp),
////            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
////            onClick = {
////                val options = ScanOptions()
////                options.setPrompt("Scan Ranger ID")
////                options.setBeepEnabled(true)
////                options.setOrientationLocked(true)
////                options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity::class.java)
////                activity.scanLauncher.launch(options)
////            }
////        ) {
////            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
////                Column(horizontalAlignment = Alignment.CenterHorizontally) {
////                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(40.dp))
////                    Text("TAP TO SCAN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
////                }
////            }
////        }
////
////        Spacer(modifier = Modifier.height(24.dp))
////        Text("Scan History", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.LightGray)
////        Spacer(modifier = Modifier.height(8.dp))
////
////        LazyColumn {
////            items(history.size) { i ->
////                val item = history[i]
////                val isValid = item.second
////                HistoryItem(
////                    icon = if (isValid) Icons.Default.CheckCircle else Icons.Default.Cancel,
////                    title = item.first,
////                    subtitle = if (isValid) "Access Granted" else "Access Denied",
////                    color = if (isValid) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
////                )
////            }
////        }
////    }
////}
////
////// --- HELPER COMPONENT ---
////@Composable
////fun HistoryItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: androidx.compose.ui.graphics.Color) {
////    Card(
////        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
////        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
////    ) {
////        Row(
////            modifier = Modifier.padding(16.dp),
////            verticalAlignment = Alignment.CenterVertically
////        ) {
////            Icon(imageVector = icon, contentDescription = null, tint = color)
////            Spacer(modifier = Modifier.width(16.dp))
////            Column {
////                Text(text = title, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
////                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.Gray)
////            }
////        }
////    }
////}
////
////// --- UTILS ---
////fun generateQRCode(content: String): Bitmap? {
////    val writer = QRCodeWriter()
////    return try {
////        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
////        val width = bitMatrix.width
////        val height = bitMatrix.height
////        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
////        for (x in 0 until width) {
////            for (y in 0 until height) {
////                bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
////            }
////        }
////        bmp
////    } catch (e: Exception) { null }
////}
/////after adding music and biometric functionality and differnet screen
////package com.example.rangervault
////
////import android.Manifest
////import android.content.pm.PackageManager
////import android.graphics.Bitmap
////import android.graphics.Color as AndroidColor
////import android.media.MediaPlayer
////import android.os.Build
////import android.os.Bundle
////import android.os.VibrationEffect
////import android.os.Vibrator
////import android.widget.Toast
////import androidx.activity.compose.setContent
////import androidx.appcompat.app.AppCompatActivity
////import androidx.biometric.BiometricManager
////import androidx.biometric.BiometricPrompt
////import androidx.compose.animation.*
////import androidx.compose.foundation.Image
////import androidx.compose.foundation.background
////import androidx.compose.foundation.clickable
////import androidx.compose.foundation.layout.*
////import androidx.compose.foundation.lazy.LazyColumn
////import androidx.compose.foundation.shape.CircleShape
////import androidx.compose.foundation.shape.RoundedCornerShape
////import androidx.compose.material.icons.Icons
////import androidx.compose.material.icons.filled.*
////import androidx.compose.material3.*
////import androidx.compose.runtime.*
////import androidx.compose.ui.Alignment
////import androidx.compose.ui.Modifier
////import androidx.compose.ui.draw.clip
////import androidx.compose.ui.graphics.Brush
////import androidx.compose.ui.graphics.asImageBitmap
////import androidx.compose.ui.graphics.vector.ImageVector
////import androidx.compose.ui.platform.LocalContext
////import androidx.compose.ui.text.font.FontWeight
////import androidx.compose.ui.unit.dp
////import androidx.compose.ui.unit.sp
////import androidx.core.content.ContextCompat
////import androidx.navigation.compose.NavHost
////import androidx.navigation.compose.composable
////import androidx.navigation.compose.currentBackStackEntryAsState
////import androidx.navigation.compose.rememberNavController
////import com.google.zxing.BarcodeFormat
////import com.google.zxing.qrcode.QRCodeWriter
////import com.journeyapps.barcodescanner.CaptureActivity
////import com.journeyapps.barcodescanner.ScanContract
////import com.journeyapps.barcodescanner.ScanOptions
////import kotlinx.coroutines.Dispatchers
////import kotlinx.coroutines.delay
////import kotlinx.coroutines.launch
////import java.text.SimpleDateFormat
////import java.util.Date
////import java.util.Locale
////
////class MainActivity : AppCompatActivity() {
////
////    // --- STATE ---
////    var isAuthenticated by mutableStateOf(false)
////    val scanHistory = mutableStateListOf<Pair<String, Boolean>>()
////    val generationHistory = mutableStateListOf<Pair<String, String>>()
////
////    // --- SCANNER LAUNCHER ---
////    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
////        if (result.contents != null) {
////            val isValid = OfflineVerifier.verify(result.contents)
////            playSound(isValid)
////            vibratePhone(isValid)
////
////            val payload = result.contents.split("##")[0]
////            val userId = try { payload.split("|")[0] } catch (e: Exception) { "Unknown" }
////
////            scanHistory.add(0, userId to isValid)
////
////            // Log to Server
////            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
////                try {
////                    NetworkClient.api.sendLog(LogRequest(userId, if (isValid) "GRANTED" else "DENIED"))
////                } catch (e: Exception) { e.printStackTrace() }
////            }
////        }
////    }
////
////    // --- CRASH-PROOF BIOMETRIC LOGIC ---
////    fun showBiometricPrompt() {
////        val biometricManager = BiometricManager.from(this)
////
////        // 1. CHECK IF HARDWARE IS READY
////        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
////            BiometricManager.BIOMETRIC_SUCCESS -> {
////                // Hardware is good, proceed safely
////            }
////            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
////            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
////            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
////                // Device can't scan right now -> Auto Unlock to prevent crash
////                Toast.makeText(this, "Scanner unavailable - Entering Dev Mode", Toast.LENGTH_SHORT).show()
////                isAuthenticated = true
////                return
////            }
////        }
////
////        // 2. SAFE AUTHENTICATION ATTEMPT
////        try {
////            val executor = ContextCompat.getMainExecutor(this)
////            val biometricPrompt = BiometricPrompt(this, executor,
////                object : BiometricPrompt.AuthenticationCallback() {
////                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
////                        super.onAuthenticationSucceeded(result)
////                        isAuthenticated = true
////                        playSound(true)
////                    }
////                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
////                        super.onAuthenticationError(errorCode, errString)
////                        // If user cancels or too many attempts, just vibrate, don't crash
////                        vibratePhone(false)
////                        Toast.makeText(applicationContext, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
////                    }
////                    override fun onAuthenticationFailed() {
////                        super.onAuthenticationFailed()
////                        vibratePhone(false)
////                    }
////                })
////
////            val promptInfo = BiometricPrompt.PromptInfo.Builder()
////                .setTitle("RangerVault Security")
////                .setSubtitle("Confirm Identity")
////                .setNegativeButtonText("Use Passcode")
////                .build()
////
////            biometricPrompt.authenticate(promptInfo)
////
////        } catch (e: Exception) {
////            // 3. FINAL CATCH-ALL: If anything crashes, just unlock.
////            e.printStackTrace()
////            Toast.makeText(this, "Biometric Error - Bypassing", Toast.LENGTH_SHORT).show()
////            isAuthenticated = true
////        }
////    }
////
////    // --- SOUND & HAPTICS ---
////    fun playSound(isSuccess: Boolean) {
////        try {
////            val soundId = if (isSuccess) R.raw.scan_success else R.raw.scan_fail
////            val mediaPlayer = MediaPlayer.create(this, soundId)
////            mediaPlayer.start()
////            mediaPlayer.setOnCompletionListener { it.release() }
////        } catch (e: Exception) { e.printStackTrace() }
////    }
////
////    fun vibratePhone(isSuccess: Boolean) {
////        try {
////            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
////            if (Build.VERSION.SDK_INT >= 26) {
////                val amplitude = if (isSuccess) 50 else VibrationEffect.DEFAULT_AMPLITUDE
////                val duration = if (isSuccess) 100L else 500L
////                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
////            } else {
////                vibrator.vibrate(200)
////            }
////        } catch (e: Exception) { e.printStackTrace() }
////    }
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        setContent {
////            RangerVaultTheme {
////                if (isAuthenticated) {
////                    MainScreen(this)
////                } else {
////                    LockScreen(
////                        onUnlockClick = { showBiometricPrompt() },
////                        onEmergencyBypass = { isAuthenticated = true } // Manual Bypass
////                    )
////                }
////            }
////        }
////    }
////}
////
////// --- THEME ---
////@Composable
////fun RangerVaultTheme(content: @Composable () -> Unit) {
////    MaterialTheme(
////        colorScheme = darkColorScheme(
////            primary = androidx.compose.ui.graphics.Color(0xFFE53935),
////            onPrimary = androidx.compose.ui.graphics.Color.White,
////            background = androidx.compose.ui.graphics.Color(0xFF000000),
////            surface = androidx.compose.ui.graphics.Color(0xFF121212),
////            surfaceVariant = androidx.compose.ui.graphics.Color(0xFF222222)
////        )
////    ) {
////        Surface(color = MaterialTheme.colorScheme.background) { content() }
////    }
////}
////
////// --- UPDATED LOCK SCREEN (With Bypass) ---
////@Composable
////fun LockScreen(onUnlockClick: () -> Unit, onEmergencyBypass: () -> Unit) {
////
////    // Auto-trigger with delay to ensure Window is attached
////    LaunchedEffect(Unit) {
////        delay(800) // Increased delay for safety
////        onUnlockClick()
////    }
////
////    Box(
////        modifier = Modifier.fillMaxSize().background(
////            Brush.verticalGradient(colors = listOf(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color(0xFF220000)))
////        ),
////        contentAlignment = Alignment.Center
////    ) {
////        Column(horizontalAlignment = Alignment.CenterHorizontally) {
////            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
////            Spacer(modifier = Modifier.height(24.dp))
////            Text("RANGER VAULT", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
////            Text("Biometric Clearance Required", color = androidx.compose.ui.graphics.Color.Gray)
////
////            Spacer(modifier = Modifier.height(48.dp))
////
////            Button(
////                onClick = onUnlockClick,
////                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
////                modifier = Modifier.height(50.dp)
////            ) {
////                Icon(Icons.Default.Fingerprint, null)
////                Spacer(modifier = Modifier.width(8.dp))
////                Text("TRY UNLOCK AGAIN")
////            }
////
////            Spacer(modifier = Modifier.height(24.dp))
////
////            // EMERGENCY BYPASS BUTTON (Small Text)
////            TextButton(onClick = onEmergencyBypass) {
////                Text("Trouble scanning? Tap to Enter", color = androidx.compose.ui.graphics.Color.Gray)
////            }
////        }
////    }
////}
////
////// --- MAIN SCREEN ---
////@OptIn(ExperimentalMaterial3Api::class)
////@Composable
////fun MainScreen(activity: MainActivity) {
////    val navController = rememberNavController()
////    val navBackStackEntry by navController.currentBackStackEntryAsState()
////    val currentRoute = navBackStackEntry?.destination?.route
////
////    Scaffold(
////        bottomBar = {
////            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Black) {
////                NavigationBarItem(selected = currentRoute=="id_screen", onClick={navController.navigate("id_screen")}, icon={Icon(Icons.Default.Fingerprint,null)}, label={Text("My ID")})
////                NavigationBarItem(selected = currentRoute=="scan_screen", onClick={navController.navigate("scan_screen")}, icon={Icon(Icons.Default.QrCodeScanner,null)}, label={Text("Scan")})
////                NavigationBarItem(selected = currentRoute=="history_screen", onClick={navController.navigate("history_screen")}, icon={Icon(Icons.Default.History,null)}, label={Text("Logs")})
////            }
////        }
////    ) { padding ->
////        NavHost(navController, startDestination = "id_screen", modifier = Modifier.padding(padding)) {
////            composable("id_screen") { IdGeneratorScreen(activity) }
////            composable("scan_screen") { ScannerScreen(activity) }
////            composable("history_screen") { HistoryScreen(activity.scanHistory, activity.generationHistory) }
////        }
////    }
////}
////
////@Composable
////fun IdGeneratorScreen(activity: MainActivity) {
////    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
////    var timeLeft by remember { mutableStateOf(0) }
////    val scope = rememberCoroutineScope()
////
////    LaunchedEffect(qrBitmap) {
////        if (qrBitmap != null) {
////            timeLeft = 30
////            while (timeLeft > 0) {
////                delay(1000L)
////                timeLeft--
////            }
////            qrBitmap = null
////            activity.vibratePhone(false)
////        }
////    }
////
////    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
////        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(24.dp), modifier = Modifier.size(300.dp)) {
////            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
////                if (qrBitmap != null) {
////                    Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)))
////                    CircularProgressIndicator(progress = { timeLeft / 30f }, modifier = Modifier.size(260.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 6.dp)
////                } else {
////                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
////                        Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = androidx.compose.ui.graphics.Color.Gray)
////                        Text("Secure Identity Locked", color = androidx.compose.ui.graphics.Color.Gray, modifier = Modifier.padding(top=8.dp))
////                    }
////                }
////            }
////        }
////        Spacer(modifier = Modifier.height(40.dp))
////        Button(onClick = {
////            activity.vibratePhone(true)
////            scope.launch {
////                try {
////                    activity.playSound(true)
////                    val response = NetworkClient.api.getIdentity(IdentityRequest("Ranger_Red", "Commander"))
////                    val finalString = "${response.payload}##${response.signature}"
////                    qrBitmap = generateQRCode(finalString)
////                    activity.generationHistory.add(0, "Commander" to SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
////                    try { NetworkClient.api.sendLog(LogRequest("Ranger_Red", "GENERATED_ID")) } catch (e: Exception) {}
////                } catch (e: Exception) { e.printStackTrace() }
////            }
////        }, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
////            Icon(Icons.Default.VpnKey, null)
////            Spacer(modifier = Modifier.width(12.dp))
////            Text("GENERATE SECURE PASS")
////        }
////    }
////}
////
////@Composable
////fun ScannerScreen(activity: MainActivity) {
////    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
////        Card(modifier = Modifier.fillMaxWidth().height(220.dp).clickable {
////            activity.vibratePhone(true)
////            val options = ScanOptions()
////            options.setPrompt("Volume Up for Flashlight")
////            options.setBeepEnabled(false)
////            options.setOrientationLocked(true)
////            options.setCaptureActivity(CaptureActivity::class.java)
////            activity.scanLauncher.launch(options)
////        }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(24.dp)) {
////            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
////                Column(horizontalAlignment = Alignment.CenterHorizontally) {
////                    Icon(Icons.Default.CameraAlt, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
////                    Text("ACTIVATE SCANNER", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top=16.dp))
////                }
////            }
////        }
////        Spacer(modifier = Modifier.height(16.dp))
////        Text("Sound & Haptics Active", color = androidx.compose.ui.graphics.Color.DarkGray)
////    }
////}
////
////@Composable
////fun HistoryScreen(scans: List<Pair<String, Boolean>>, generations: List<Pair<String, String>>) {
////    var selectedTab by remember { mutableStateOf(0) }
////    Column(modifier = Modifier.fillMaxSize()) {
////        TabRow(selectedTabIndex = selectedTab, containerColor = androidx.compose.ui.graphics.Color.Black) {
////            Tab(selected = selectedTab==0, onClick = { selectedTab=0 }, text = { Text("SCANS") }, icon = { Icon(Icons.Default.QrCodeScanner, null) })
////            Tab(selected = selectedTab==1, onClick = { selectedTab=1 }, text = { Text("GENERATED") }, icon = { Icon(Icons.Default.Fingerprint, null) })
////        }
////        LazyColumn(contentPadding = PaddingValues(16.dp)) {
////            if(selectedTab == 0) {
////                items(scans.size) { i ->
////                    val item = scans[i]
////                    HistoryItem(if(item.second) Icons.Default.CheckCircle else Icons.Default.Cancel, item.first, if(item.second) "Granted" else "Denied", if(item.second) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red)
////                }
////            } else {
////                items(generations.size) { i ->
////                    val item = generations[i]
////                    HistoryItem(Icons.Default.History, item.first, "Time: ${item.second}", MaterialTheme.colorScheme.primary)
////                }
////            }
////        }
////    }
////}
////
////@Composable
////fun HistoryItem(icon: ImageVector, title: String, subtitle: String, color: androidx.compose.ui.graphics.Color) {
////    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
////        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
////            Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
////            Column(modifier = Modifier.padding(start=16.dp)) {
////                Text(title, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
////                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.LightGray)
////            }
////        }
////    }
////}
////
////fun generateQRCode(content: String): Bitmap? {
////    val writer = QRCodeWriter()
////    return try {
////        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
////        val width = bitMatrix.width
////        val height = bitMatrix.height
////        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
////        for (x in 0 until width) {
////            for (y in 0 until height) {
////                bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
////            }
////        }
////        bmp
////    } catch (e: Exception) { null }
////}