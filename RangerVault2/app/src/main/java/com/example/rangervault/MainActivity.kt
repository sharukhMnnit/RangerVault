package com.example.rangervault // <--- MAKE SURE THIS MATCHES YOUR PACKAGE NAME

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

// Scanner Libraries
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

// 1. Define the Data Models
data class IdentityRequest(val userId: String, val role: String)
data class IdentityResponse(val payload: String, val signature: String)

// --- NEW: Model for sending logs to Dashboard ---
data class LogRequest(val userId: String, val status: String)

// 2. Define the API Interface
interface RangerApi {
    @POST("/api/generate-identity")
    suspend fun getIdentity(@Body request: IdentityRequest): IdentityResponse

    // --- NEW: Function to report scans to Server ---
    @POST("/api/log-entry")
    suspend fun sendLog(@Body request: LogRequest): okhttp3.ResponseBody
}

// 3. Setup Retrofit (Network Client)
// Your IP is preserved here: 172.29.59.202
val retrofit = Retrofit.Builder()
//    .baseUrl("http://172.29.59.202:3000/")
    .baseUrl("http://10.0.2.2:3000/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val api = retrofit.create(RangerApi::class.java)

class MainActivity : ComponentActivity() {

    // --- SCANNER HANDLER (Updated with Logging) ---
    val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(this, "Scan Cancelled", Toast.LENGTH_SHORT).show()
        } else {
            // 1. Verify the ID Locally (Offline Math)
            val isValid = OfflineVerifier.verify(result.contents)

            // 2. Prepare Data for Dashboard Log
            // Structure is "User|Role|Time##Signature". We just want "User".
            val payload = result.contents.split("##")[0]
            val userId = try { payload.split("|")[0] } catch (e: Exception) { "Unknown" }
            val status = if (isValid) "GRANTED" else "DENIED"

            // 3. Show UI Feedback
            if (isValid) {
                Toast.makeText(this, "✅ ACCESS GRANTED: Valid Ranger ID", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "❌ ACCESS DENIED: Fake or Expired ID", Toast.LENGTH_LONG).show()
            }

            // 4. SEND LOG TO SERVER (Background Thread)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    api.sendLog(LogRequest(userId, status))
                } catch (e: Exception) {
                    e.printStackTrace()
                    // If server is down, we just ignore the log error. Verification still worked!
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RangerVaultApp()
        }
    }
}

@Composable
fun RangerVaultApp() {
    // UI State
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusText by remember { mutableStateOf("Ready to authenticate") }
    val scope = rememberCoroutineScope()

    // We need Context to trigger the Scanner from the Button
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "RangerVault Identity", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(32.dp))

        // Show QR Code if it exists
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap!!.asImageBitmap(),
                contentDescription = "Identity QR",
                modifier = Modifier.size(250.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Valid for 30 seconds", color = MaterialTheme.colorScheme.primary)
        } else {
            // Placeholder box
            Surface(
                modifier = Modifier.size(250.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("No Active Pass")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(text = statusText)
        Spacer(modifier = Modifier.height(16.dp))

        // BUTTON 1: Generate ID
        // BUTTON 1: Generate ID
        Button(onClick = {
            scope.launch {
                statusText = "Contacting HQ..."
                try {
                    // 1. Get ID from Server
                    val response = api.getIdentity(IdentityRequest("Ranger_Red", "Leader"))
                    val finalString = "${response.payload}##${response.signature}"
                    qrBitmap = generateQRCode(finalString)
                    statusText = "Identity Secure & Active"

                    // 2. --- NEW: Send a Log to Dashboard immediately! ---
                    // This ensures you see activity on the web page even without scanning.
                    try {
                        api.sendLog(LogRequest("Ranger_Red", "GENERATED_NEW_ID"))
                    } catch (e: Exception) {
                        // Ignore log errors
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    statusText = "Error: ${e.localizedMessage}"
                }
            }
        }) {
            Text("Generate Secure ID")
        }
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

        Spacer(modifier = Modifier.height(16.dp))

        // BUTTON 2: Verify ID (Scanner)
        Button(
            onClick = {
                val options = ScanOptions()
                options.setPrompt("Scan Ranger ID to Verify")
                options.setBeepEnabled(true)
                options.setOrientationLocked(false)

                // Launch the scanner defined in MainActivity
                (context as MainActivity).scanLauncher.launch(options)
            },
            // Using correct Compose Color reference
            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red)
        ) {
            Text("VERIFY ID (Scanner Mode)")
        }
    }
}

// Helper Function to draw QR
fun generateQRCode(content: String): Bitmap? {
    val writer = QRCodeWriter()
    return try {
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        null
    }
}









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