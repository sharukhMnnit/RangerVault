package com.example.rangervault.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rangervault.viewmodel.RangerViewModel
import kotlinx.coroutines.delay

@Composable
fun IdGeneratorScreen(viewModel: RangerViewModel, onGeoLogReq: () -> Unit) {
    var timeLeft by remember { mutableStateOf(0) }

    LaunchedEffect(viewModel.qrBitmap) {
        if (viewModel.qrBitmap != null) {
            onGeoLogReq()
            timeLeft = 30; while (timeLeft > 0) { delay(1000L); timeLeft-- }; viewModel.qrBitmap = null
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (viewModel.qrBitmap != null) {
            Image(bitmap = viewModel.qrBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)))
            Text("${timeLeft}s", color = MaterialTheme.colorScheme.primary, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top=16.dp))
            Text("TOKEN EXPIRING", color = Color.Gray, fontSize = 12.sp)
        } else {
            Icon(Icons.Default.Shield, null, Modifier.size(80.dp), tint = Color.DarkGray)
            Spacer(Modifier.height(16.dp))
            Text(viewModel.currentUserName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Security Level: ${viewModel.currentUserRole}", color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { viewModel.generateIdentityToken() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("GENERATE SECURE TOKEN", color = Color.Black, fontWeight = FontWeight.Bold) }
    }
}