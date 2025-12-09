package com.example.rangervault.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.rangervault.utils.PortraitCaptureActivity
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun ScannerScreen(scanLauncher: (ScanOptions) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 200f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse)
    )

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(250.dp).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)).clickable {
            val options = ScanOptions()
            options.setCaptureActivity(PortraitCaptureActivity::class.java)
            options.setOrientationLocked(true); options.setBeepEnabled(false)
            scanLauncher(options)
        }, contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(2.dp).offset(y = offsetY.dp).background(Color.Red))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.QrCodeScanner, null, tint = Color.Gray, modifier = Modifier.size(50.dp))
                Text("Tap to Scan", color = Color.Gray)
            }
        }
    }
}