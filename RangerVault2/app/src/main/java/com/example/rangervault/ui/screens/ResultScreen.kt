package com.example.rangervault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ResultScreen(isSuccess: Boolean, username: String, onBack: () -> Unit) {
    val bgColor = if (isSuccess) Color(0xFF004D40) else Color(0xFF4A0000)
    val iconColor = if (isSuccess) Color(0xFF00E676) else Color(0xFFFF1744)
    val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Close

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(32.dp))
        Text(if (isSuccess) "ACCESS GRANTED" else "ACCESS DENIED", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(if (isSuccess) "Identity Verified" else "Security Protocol Violation", color = Color.LightGray, fontSize = 14.sp)
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
            onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("RETURN TO SCANNER", color = Color.Black, fontWeight = FontWeight.Bold) }
    }
}