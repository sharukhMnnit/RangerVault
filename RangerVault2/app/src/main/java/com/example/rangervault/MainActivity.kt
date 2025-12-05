//package com.example.rangervault // <--- MAKE SURE THIS MATCHES YOUR PACKAGE NAME
//
//import android.graphics.Bitmap
//import android.graphics.Color
//import android.os.Bundle
//import android.widget.Toast
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.launch
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//import retrofit2.http.Body
//import retrofit2.http.POST
//
//import com.google.zxing.BarcodeFormat
//import com.google.zxing.qrcode.QRCodeWriter
//
//// Scanner Libraries
//import com.journeyapps.barcodescanner.ScanContract
//import com.journeyapps.barcodescanner.ScanOptions
//
//// 1. Define the Data Models
//data class IdentityRequest(val userId: String, val role: String)
//data class IdentityResponse(val payload: String, val signature: String)
//
//// --- NEW: Model for sending logs to Dashboard ---
//data class LogRequest(val userId: String, val status: String)
//
//// 2. Define the API Interface
//interface RangerApi {
//    @POST("/api/generate-identity")
//    suspend fun getIdentity(@Body request: IdentityRequest): IdentityResponse
//
//    // --- NEW: Function to report scans to Server ---
//    @POST("/api/log-entry")
//    suspend fun sendLog(@Body request: LogRequest): okhttp3.ResponseBody
//}
//
//// 3. Setup Retrofit (Network Client)
//// Your IP is preserved here: 172.29.59.202
//val retrofit = Retrofit.Builder()
////    .baseUrl("http://172.29.59.202:3000/")
//    .baseUrl("http://10.0.2.2:3000/")
//    .addConverterFactory(GsonConverterFactory.create())
//    .build()
//
//val api = retrofit.create(RangerApi::class.java)
//
//class MainActivity : ComponentActivity() {
//
//    // --- SCANNER HANDLER (Updated with Logging) ---
//    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
//        if (result.contents == null) {
//            Toast.makeText(this, "Scan Cancelled", Toast.LENGTH_SHORT).show()
//        } else {
//            // 1. Verify the ID Locally (Offline Math)
//            val isValid = OfflineVerifier.verify(result.contents)
//
//            // 2. Prepare Data for Dashboard Log
//            // Structure is "User|Role|Time##Signature". We just want "User".
//            val payload = result.contents.split("##")[0]
//            val userId = try { payload.split("|")[0] } catch (e: Exception) { "Unknown" }
//            val status = if (isValid) "GRANTED" else "DENIED"
//
//            // 3. Show UI Feedback
//            if (isValid) {
//                Toast.makeText(this, "✅ ACCESS GRANTED: Valid Ranger ID", Toast.LENGTH_LONG).show()
//            } else {
//                Toast.makeText(this, "❌ ACCESS DENIED: Fake or Expired ID", Toast.LENGTH_LONG).show()
//            }
//
//            // 4. SEND LOG TO SERVER (Background Thread)
//            CoroutineScope(Dispatchers.IO).launch {
//                try {
//                    api.sendLog(LogRequest(userId, status))
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                    // If server is down, we just ignore the log error. Verification still worked!
//                }
//            }
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            RangerVaultApp()
//        }
//    }
//}
//
//@Composable
//fun RangerVaultApp() {
//    // UI State
//    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
//    var statusText by remember { mutableStateOf("Ready to authenticate") }
//    val scope = rememberCoroutineScope()
//
//    // We need Context to trigger the Scanner from the Button
//    val context = LocalContext.current
//
//    Column(
//        modifier = Modifier.fillMaxSize().padding(24.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        Text(text = "RangerVault Identity", style = MaterialTheme.typography.headlineMedium)
//
//        Spacer(modifier = Modifier.height(32.dp))
//
//        // Show QR Code if it exists
//        if (qrBitmap != null) {
//            Image(
//                bitmap = qrBitmap!!.asImageBitmap(),
//                contentDescription = "Identity QR",
//                modifier = Modifier.size(250.dp)
//            )
//            Spacer(modifier = Modifier.height(16.dp))
//            Text("Valid for 30 seconds", color = MaterialTheme.colorScheme.primary)
//        } else {
//            // Placeholder box
//            Surface(
//                modifier = Modifier.size(250.dp),
//                color = MaterialTheme.colorScheme.surfaceVariant
//            ) {
//                Box(contentAlignment = Alignment.Center) {
//                    Text("No Active Pass")
//                }
//            }
//        }
//
//        Spacer(modifier = Modifier.height(32.dp))
//        Text(text = statusText)
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // BUTTON 1: Generate ID
//        // BUTTON 1: Generate ID
//        Button(onClick = {
//            scope.launch {
//                statusText = "Contacting HQ..."
//                try {
//                    // 1. Get ID from Server
//                    val response = api.getIdentity(IdentityRequest("Ranger_Red", "Leader"))
//                    val finalString = "${response.payload}##${response.signature}"
//                    qrBitmap = generateQRCode(finalString)
//                    statusText = "Identity Secure & Active"
//
//                    // 2. --- NEW: Send a Log to Dashboard immediately! ---
//                    // This ensures you see activity on the web page even without scanning.
//                    try {
//                        api.sendLog(LogRequest("Ranger_Red", "GENERATED_NEW_ID"))
//                    } catch (e: Exception) {
//                        // Ignore log errors
//                    }
//
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                    statusText = "Error: ${e.localizedMessage}"
//                }
//            }
//        }) {
//            Text("Generate Secure ID")
//        }
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
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // BUTTON 2: Verify ID (Scanner)
//        Button(
//            onClick = {
//                val options = ScanOptions()
//                options.setPrompt("Scan Ranger ID to Verify")
//                options.setBeepEnabled(true)
//                options.setOrientationLocked(false)
//
//                // Launch the scanner defined in MainActivity
//                (context as MainActivity).scanLauncher.launch(options)
//            },
//            // Using correct Compose Color reference
//            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red)
//        ) {
//            Text("VERIFY ID (Scanner Mode)")
//        }
//    }
//}
//
//// Helper Function to draw QR
//fun generateQRCode(content: String): Bitmap? {
//    val writer = QRCodeWriter()
//    return try {
//        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
//        val width = bitMatrix.width
//        val height = bitMatrix.height
//        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
//        for (x in 0 until width) {
//            for (y in 0 until height) {
//                bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
//            }
//        }
//        bmp
//    } catch (e: Exception) {
//        null
//    }
//}









//package com.example.rangervault // <--- MAKE SURE THIS MATCHES YOUR PACKAGE NAME
//
//import android.graphics.Bitmap
//import android.graphics.Color
//import android.os.Bundle
//import android.widget.Toast
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import com.google.zxing.BarcodeFormat
//import com.google.zxing.qrcode.QRCodeWriter
//import kotlinx.coroutines.launch
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//import retrofit2.http.Body
//import retrofit2.http.POST
//
//// Scanner Libraries
//import com.journeyapps.barcodescanner.ScanContract
//import com.journeyapps.barcodescanner.ScanOptions
//
//// 1. Define the Data Models
//data class IdentityRequest(val userId: String, val role: String)
//data class IdentityResponse(val payload: String, val signature: String)
//
//// 2. Define the API Interface
//interface RangerApi {
//    @POST("/api/generate-identity")
//    suspend fun getIdentity(@Body request: IdentityRequest): IdentityResponse
//}
//
//// 3. Setup Retrofit (Network Client)
//// NOTE: Since you are on a VIVO Phone, change "10.0.2.2" to your Laptop's Wi-Fi IP (e.g., "192.168.1.X")
//val retrofit = Retrofit.Builder()
//    .baseUrl("http://172.29.59.202:3000/")
//    .addConverterFactory(GsonConverterFactory.create())
//    .build()
//
//val api = retrofit.create(RangerApi::class.java)
//
//class MainActivity : ComponentActivity() {
//
//    // --- NEW: Scanner Launcher handles the result from the Camera ---
//    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
//        if (result.contents == null) {
//            Toast.makeText(this, "Scan Cancelled", Toast.LENGTH_SHORT).show()
//        } else {
//            // We got a QR Code! Send it to the OfflineVerifier
//            // Make sure OfflineVerifier.kt exists!
//            val isValid = OfflineVerifier.verify(result.contents)
//
//            if (isValid) {
//                Toast.makeText(this, "✅ ACCESS GRANTED: Valid Ranger ID", Toast.LENGTH_LONG).show()
//            } else {
//                Toast.makeText(this, "❌ ACCESS DENIED: Fake or Expired ID", Toast.LENGTH_LONG).show()
//            }
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            RangerVaultApp()
//        }
//    }
//}
//
//@Composable
//fun RangerVaultApp() {
//    // UI State
//    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
//    var statusText by remember { mutableStateOf("Ready to authenticate") }
//    val scope = rememberCoroutineScope()
//
//    // We need Context to trigger the Scanner from the Button
//    val context = LocalContext.current
//
//    Column(
//        modifier = Modifier.fillMaxSize().padding(24.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        Text(text = "RangerVault Identity", style = MaterialTheme.typography.headlineMedium)
//
//        Spacer(modifier = Modifier.height(32.dp))
//
//        // Show QR Code if it exists
//        if (qrBitmap != null) {
//            Image(
//                bitmap = qrBitmap!!.asImageBitmap(),
//                contentDescription = "Identity QR",
//                modifier = Modifier.size(250.dp)
//            )
//            Spacer(modifier = Modifier.height(16.dp))
//            Text("Valid for 30 seconds", color = MaterialTheme.colorScheme.primary)
//        } else {
//            // Placeholder box
//            Surface(
//                modifier = Modifier.size(250.dp),
//                color = MaterialTheme.colorScheme.surfaceVariant
//            ) {
//                Box(contentAlignment = Alignment.Center) {
//                    Text("No Active Pass")
//                }
//            }
//        }
//
//        Spacer(modifier = Modifier.height(32.dp))
//        Text(text = statusText)
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // BUTTON 1: Generate ID
//        Button(onClick = {
//            scope.launch {
//                statusText = "Contacting HQ..."
//                try {
//                    // Call the Server
//                    val response = api.getIdentity(IdentityRequest("Ranger_Red", "Leader"))
//
//                    // Combine Data
//                    val finalString = "${response.payload}##${response.signature}"
//
//                    // Generate QR
//                    qrBitmap = generateQRCode(finalString)
//                    statusText = "Identity Secure & Active"
//
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                    statusText = "Error: ${e.localizedMessage}"
//                }
//            }
//        }) {
//            Text("Generate Secure ID")
//        }
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // --- NEW BUTTON 2: Verify ID (Scanner) ---
//        Button(
//            onClick = {
//                val options = ScanOptions()
//                options.setPrompt("Scan Ranger ID to Verify")
//                options.setBeepEnabled(true)
//                options.setOrientationLocked(false)
//
//                // Launch the scanner defined in MainActivity
//                (context as MainActivity).scanLauncher.launch(options)
//            },
//            // Use the full name "androidx.compose.ui.graphics.Color"
//            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red)
//        ) {
//            Text("VERIFY ID (Scanner Mode)")
//        }
//    }
//}
//
//// Helper Function to draw QR
//fun generateQRCode(content: String): Bitmap? {
//    val writer = QRCodeWriter()
//    return try {
//        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
//        val width = bitMatrix.width
//        val height = bitMatrix.height
//        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
//        for (x in 0 until width) {
//            for (y in 0 until height) {
//                bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
//            }
//        }
//        bmp
//    } catch (e: Exception) {
//        null
//    }
//}
//updatedcode


//
//package com.example.rangervault
//
//import android.graphics.Bitmap
//import android.graphics.Color as AndroidColor
//import android.os.Bundle
//import android.widget.Toast
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.navigation.compose.NavHost
//import androidx.navigation.compose.composable
//import androidx.navigation.compose.rememberNavController
//import com.google.zxing.BarcodeFormat
//import com.google.zxing.qrcode.QRCodeWriter
//import com.journeyapps.barcodescanner.ScanContract
//import com.journeyapps.barcodescanner.ScanOptions
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import java.text.SimpleDateFormat
//import java.util.Date
//import java.util.Locale
//
//class MainActivity : ComponentActivity() {
//
//    // --- STATE: History Lists ---
//    // 1. People you scanned
//    val scanHistory = mutableStateListOf<Pair<String, Boolean>>()
//    // 2. IDs you generated
//    val generationHistory = mutableStateListOf<Pair<String, String>>()
//
//    // --- SCANNER LAUNCHER ---
//    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
//        if (result.contents != null) {
//            val isValid = OfflineVerifier.verify(result.contents)
//
//            // Extract Name
//            val payload = result.contents.split("##")[0]
//            val userId = try { payload.split("|")[0] } catch (e: Exception) { "Unknown" }
//
//            // Update UI History
//            scanHistory.add(0, userId to isValid)
//
//            // Show Toast
//            val msg = if (isValid) "✅ ACCESS GRANTED" else "❌ ACCESS DENIED"
//            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
//
//            // Log to Server (Background)
//            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
//                try {
//                    NetworkClient.api.sendLog(LogRequest(userId, if (isValid) "GRANTED" else "DENIED"))
//                } catch (e: Exception) { e.printStackTrace() }
//            }
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            RangerVaultTheme {
//                MainScreen(this)
//            }
//        }
//    }
//}
//
//// --- THEME ---
//@Composable
//fun RangerVaultTheme(content: @Composable () -> Unit) {
//    MaterialTheme(
//        colorScheme = darkColorScheme(
//            primary = androidx.compose.ui.graphics.Color(0xFFE53935), // Ranger Red
//            secondary = androidx.compose.ui.graphics.Color(0xFF1E88E5),
//            background = androidx.compose.ui.graphics.Color(0xFF121212),
//            surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E)
//        )
//    ) {
//        Surface(color = MaterialTheme.colorScheme.background) {
//            content()
//        }
//    }
//}
//
//// --- MAIN NAVIGATION ---
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun MainScreen(activity: MainActivity) {
//    val navController = rememberNavController()
//
//    Scaffold(
//        bottomBar = {
//            NavigationBar(containerColor = androidx.compose.ui.graphics.Color(0xFF000000)) {
//                NavigationBarItem(
//                    selected = navController.currentDestination?.route == "id_screen",
//                    onClick = { navController.navigate("id_screen") },
//                    icon = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
//                    label = { Text("My ID") }
//                )
//                NavigationBarItem(
//                    selected = navController.currentDestination?.route == "scan_screen",
//                    onClick = { navController.navigate("scan_screen") },
//                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
//                    label = { Text("Scanner") }
//                )
//            }
//        }
//    ) { padding ->
//        NavHost(
//            navController = navController,
//            startDestination = "id_screen",
//            modifier = Modifier.padding(padding)
//        ) {
//            composable("id_screen") {
//                IdGeneratorScreen(activity.generationHistory)
//            }
//            composable("scan_screen") {
//                ScannerScreen(activity)
//            }
//        }
//    }
//}
//
//// --- SCREEN 1: GENERATOR ---
//@Composable
//fun IdGeneratorScreen(history: MutableList<Pair<String, String>>) {
//    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
//    var timeLeft by remember { mutableStateOf(0) }
//    var statusText by remember { mutableStateOf("Tap below to generate") }
//    val scope = rememberCoroutineScope()
//
//    // Timer Logic
//    LaunchedEffect(qrBitmap) {
//        if (qrBitmap != null) {
//            timeLeft = 30
//            while (timeLeft > 0) {
//                delay(1000L)
//                timeLeft--
//            }
//            qrBitmap = null
//            statusText = "ID Expired."
//        }
//    }
//
//    Column(
//        modifier = Modifier.fillMaxSize().padding(16.dp),
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        // Upper Card
//        Card(
//            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
//            modifier = Modifier.fillMaxWidth().height(280.dp)
//        ) {
//            Column(
//                modifier = Modifier.fillMaxSize(),
//                horizontalAlignment = Alignment.CenterHorizontally,
//                verticalArrangement = Arrangement.Center
//            ) {
//                if (qrBitmap != null) {
//                    Image(
//                        bitmap = qrBitmap!!.asImageBitmap(),
//                        contentDescription = "QR",
//                        modifier = Modifier.size(180.dp)
//                    )
//                    Spacer(modifier = Modifier.height(10.dp))
//                    LinearProgressIndicator(progress = { timeLeft / 30f })
//                    Spacer(modifier = Modifier.height(5.dp))
//                    Text("Valid: ${timeLeft}s", color = androidx.compose.ui.graphics.Color.White)
//                } else {
//                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(60.dp), tint = androidx.compose.ui.graphics.Color.Gray)
//                    Text("Secure Vault Locked", color = androidx.compose.ui.graphics.Color.Gray)
//                }
//            }
//        }
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // Generate Button
//        Button(
//            onClick = {
//                scope.launch {
//                    statusText = " contacting HQ..."
//                    try {
//                        val role = "Commander"
//                        val response = NetworkClient.api.getIdentity(IdentityRequest("Ranger_Red", role))
//                        val finalString = "${response.payload}##${response.signature}"
//                        qrBitmap = generateQRCode(finalString)
//                        statusText = "Identity Active"
//
//                        // Add to History
//                        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
//                        history.add(0, role to time)
//
//                        // Log to Server
//                        try { NetworkClient.api.sendLog(LogRequest("Ranger_Red", "GENERATED_ID")) } catch (e: Exception) {}
//
//                    } catch (e: Exception) {
//                        statusText = "Network Error"
//                        e.printStackTrace()
//                    }
//                }
//            },
//            modifier = Modifier.fillMaxWidth().height(50.dp),
//            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
//        ) {
//            Text("GENERATE SECURE PASS")
//        }
//
//        Text(statusText, modifier = Modifier.padding(8.dp), color = androidx.compose.ui.graphics.Color.Gray)
//
//        Spacer(modifier = Modifier.height(8.dp))
//        Divider(color = androidx.compose.ui.graphics.Color.DarkGray)
//        Spacer(modifier = Modifier.height(8.dp))
//
//        // History List
//        Text("Generation Log", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.LightGray, modifier = Modifier.align(Alignment.Start))
//
//        LazyColumn {
//            items(history.size) { i ->
//                val item = history[i]
//                HistoryItem(icon = Icons.Default.History, title = item.first, subtitle = "Time: ${item.second}", color = androidx.compose.ui.graphics.Color.White)
//            }
//        }
//    }
//}
//
//// --- SCREEN 2: SCANNER ---
//@Composable
//fun ScannerScreen(activity: MainActivity) {
//    val history = activity.scanHistory
//
//    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
//
//        // Scan Button
//        Card(
//            modifier = Modifier.fillMaxWidth().height(120.dp),
//            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
//            onClick = {
//                val options = ScanOptions()
//                options.setPrompt("Scan Ranger ID")
//                options.setBeepEnabled(true)
//                options.setOrientationLocked(true)
//                options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity::class.java)
//                activity.scanLauncher.launch(options)
//            }
//        ) {
//            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//                Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(40.dp))
//                    Text("TAP TO SCAN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
//                }
//            }
//        }
//
//        Spacer(modifier = Modifier.height(24.dp))
//        Text("Scan History", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.LightGray)
//        Spacer(modifier = Modifier.height(8.dp))
//
//        LazyColumn {
//            items(history.size) { i ->
//                val item = history[i]
//                val isValid = item.second
//                HistoryItem(
//                    icon = if (isValid) Icons.Default.CheckCircle else Icons.Default.Cancel,
//                    title = item.first,
//                    subtitle = if (isValid) "Access Granted" else "Access Denied",
//                    color = if (isValid) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
//                )
//            }
//        }
//    }
//}
//
//// --- HELPER COMPONENT ---
//@Composable
//fun HistoryItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: androidx.compose.ui.graphics.Color) {
//    Card(
//        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
//        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
//    ) {
//        Row(
//            modifier = Modifier.padding(16.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Icon(imageVector = icon, contentDescription = null, tint = color)
//            Spacer(modifier = Modifier.width(16.dp))
//            Column {
//                Text(text = title, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
//                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.Gray)
//            }
//        }
//    }
//}
//
//// --- UTILS ---
//fun generateQRCode(content: String): Bitmap? {
//    val writer = QRCodeWriter()
//    return try {
//        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
//        val width = bitMatrix.width
//        val height = bitMatrix.height
//        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
//        for (x in 0 until width) {
//            for (y in 0 until height) {
//                bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
//            }
//        }
//        bmp
//    } catch (e: Exception) { null }
//}
///after adding music and biometric functionality and differnet screen
package com.example.rangervault

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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

class MainActivity : AppCompatActivity() {

    // --- STATE ---
    var isAuthenticated by mutableStateOf(false)
    val scanHistory = mutableStateListOf<Pair<String, Boolean>>()
    val generationHistory = mutableStateListOf<Pair<String, String>>()

    // --- SCANNER LAUNCHER ---
    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val isValid = OfflineVerifier.verify(result.contents)
            playSound(isValid)
            vibratePhone(isValid)

            val payload = result.contents.split("##")[0]
            val userId = try { payload.split("|")[0] } catch (e: Exception) { "Unknown" }

            scanHistory.add(0, userId to isValid)

            // Log to Server
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    NetworkClient.api.sendLog(LogRequest(userId, if (isValid) "GRANTED" else "DENIED"))
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    // --- CRASH-PROOF BIOMETRIC LOGIC ---
    fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)

        // 1. CHECK IF HARDWARE IS READY
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Hardware is good, proceed safely
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // Device can't scan right now -> Auto Unlock to prevent crash
                Toast.makeText(this, "Scanner unavailable - Entering Dev Mode", Toast.LENGTH_SHORT).show()
                isAuthenticated = true
                return
            }
        }

        // 2. SAFE AUTHENTICATION ATTEMPT
        try {
            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        isAuthenticated = true
                        playSound(true)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        // If user cancels or too many attempts, just vibrate, don't crash
                        vibratePhone(false)
                        Toast.makeText(applicationContext, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                    }
                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        vibratePhone(false)
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("RangerVault Security")
                .setSubtitle("Confirm Identity")
                .setNegativeButtonText("Use Passcode")
                .build()

            biometricPrompt.authenticate(promptInfo)

        } catch (e: Exception) {
            // 3. FINAL CATCH-ALL: If anything crashes, just unlock.
            e.printStackTrace()
            Toast.makeText(this, "Biometric Error - Bypassing", Toast.LENGTH_SHORT).show()
            isAuthenticated = true
        }
    }

    // --- SOUND & HAPTICS ---
    fun playSound(isSuccess: Boolean) {
        try {
            val soundId = if (isSuccess) R.raw.scan_success else R.raw.scan_fail
            val mediaPlayer = MediaPlayer.create(this, soundId)
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener { it.release() }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun vibratePhone(isSuccess: Boolean) {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) {
                val amplitude = if (isSuccess) 50 else VibrationEffect.DEFAULT_AMPLITUDE
                val duration = if (isSuccess) 100L else 500L
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                vibrator.vibrate(200)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RangerVaultTheme {
                if (isAuthenticated) {
                    MainScreen(this)
                } else {
                    LockScreen(
                        onUnlockClick = { showBiometricPrompt() },
                        onEmergencyBypass = { isAuthenticated = true } // Manual Bypass
                    )
                }
            }
        }
    }
}

// --- THEME ---
@Composable
fun RangerVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFFE53935),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            background = androidx.compose.ui.graphics.Color(0xFF000000),
            surface = androidx.compose.ui.graphics.Color(0xFF121212),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFF222222)
        )
    ) {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}

// --- UPDATED LOCK SCREEN (With Bypass) ---
@Composable
fun LockScreen(onUnlockClick: () -> Unit, onEmergencyBypass: () -> Unit) {

    // Auto-trigger with delay to ensure Window is attached
    LaunchedEffect(Unit) {
        delay(800) // Increased delay for safety
        onUnlockClick()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(colors = listOf(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color(0xFF220000)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text("RANGER VAULT", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            Text("Biometric Clearance Required", color = androidx.compose.ui.graphics.Color.Gray)

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onUnlockClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(50.dp)
            ) {
                Icon(Icons.Default.Fingerprint, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("TRY UNLOCK AGAIN")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // EMERGENCY BYPASS BUTTON (Small Text)
            TextButton(onClick = onEmergencyBypass) {
                Text("Trouble scanning? Tap to Enter", color = androidx.compose.ui.graphics.Color.Gray)
            }
        }
    }
}

// --- MAIN SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(activity: MainActivity) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Black) {
                NavigationBarItem(selected = currentRoute=="id_screen", onClick={navController.navigate("id_screen")}, icon={Icon(Icons.Default.Fingerprint,null)}, label={Text("My ID")})
                NavigationBarItem(selected = currentRoute=="scan_screen", onClick={navController.navigate("scan_screen")}, icon={Icon(Icons.Default.QrCodeScanner,null)}, label={Text("Scan")})
                NavigationBarItem(selected = currentRoute=="history_screen", onClick={navController.navigate("history_screen")}, icon={Icon(Icons.Default.History,null)}, label={Text("Logs")})
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = "id_screen", modifier = Modifier.padding(padding)) {
            composable("id_screen") { IdGeneratorScreen(activity) }
            composable("scan_screen") { ScannerScreen(activity) }
            composable("history_screen") { HistoryScreen(activity.scanHistory, activity.generationHistory) }
        }
    }
}

@Composable
fun IdGeneratorScreen(activity: MainActivity) {
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var timeLeft by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(qrBitmap) {
        if (qrBitmap != null) {
            timeLeft = 30
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft--
            }
            qrBitmap = null
            activity.vibratePhone(false)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(24.dp), modifier = Modifier.size(300.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (qrBitmap != null) {
                    Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)))
                    CircularProgressIndicator(progress = { timeLeft / 30f }, modifier = Modifier.size(260.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 6.dp)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = androidx.compose.ui.graphics.Color.Gray)
                        Text("Secure Identity Locked", color = androidx.compose.ui.graphics.Color.Gray, modifier = Modifier.padding(top=8.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        Button(onClick = {
            activity.vibratePhone(true)
            scope.launch {
                try {
                    activity.playSound(true)
                    val response = NetworkClient.api.getIdentity(IdentityRequest("Ranger_Red", "Commander"))
                    val finalString = "${response.payload}##${response.signature}"
                    qrBitmap = generateQRCode(finalString)
                    activity.generationHistory.add(0, "Commander" to SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
                    try { NetworkClient.api.sendLog(LogRequest("Ranger_Red", "GENERATED_ID")) } catch (e: Exception) {}
                } catch (e: Exception) { e.printStackTrace() }
            }
        }, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Icon(Icons.Default.VpnKey, null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("GENERATE SECURE PASS")
        }
    }
}

@Composable
fun ScannerScreen(activity: MainActivity) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Card(modifier = Modifier.fillMaxWidth().height(220.dp).clickable {
            activity.vibratePhone(true)
            val options = ScanOptions()
            options.setPrompt("Volume Up for Flashlight")
            options.setBeepEnabled(false)
            options.setOrientationLocked(true)
            options.setCaptureActivity(CaptureActivity::class.java)
            activity.scanLauncher.launch(options)
        }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(24.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("ACTIVATE SCANNER", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top=16.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Sound & Haptics Active", color = androidx.compose.ui.graphics.Color.DarkGray)
    }
}

@Composable
fun HistoryScreen(scans: List<Pair<String, Boolean>>, generations: List<Pair<String, String>>) {
    var selectedTab by remember { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab, containerColor = androidx.compose.ui.graphics.Color.Black) {
            Tab(selected = selectedTab==0, onClick = { selectedTab=0 }, text = { Text("SCANS") }, icon = { Icon(Icons.Default.QrCodeScanner, null) })
            Tab(selected = selectedTab==1, onClick = { selectedTab=1 }, text = { Text("GENERATED") }, icon = { Icon(Icons.Default.Fingerprint, null) })
        }
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            if(selectedTab == 0) {
                items(scans.size) { i ->
                    val item = scans[i]
                    HistoryItem(if(item.second) Icons.Default.CheckCircle else Icons.Default.Cancel, item.first, if(item.second) "Granted" else "Denied", if(item.second) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red)
                }
            } else {
                items(generations.size) { i ->
                    val item = generations[i]
                    HistoryItem(Icons.Default.History, item.first, "Time: ${item.second}", MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(icon: ImageVector, title: String, subtitle: String, color: androidx.compose.ui.graphics.Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.padding(start=16.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.LightGray)
            }
        }
    }
}

fun generateQRCode(content: String): Bitmap? {
    val writer = QRCodeWriter()
    return try {
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bmp
    } catch (e: Exception) { null }
}