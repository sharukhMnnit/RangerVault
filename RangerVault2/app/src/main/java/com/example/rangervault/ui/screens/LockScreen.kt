package com.example.rangervault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

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