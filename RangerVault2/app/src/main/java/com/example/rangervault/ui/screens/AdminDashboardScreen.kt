package com.example.rangervault.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rangervault.viewmodel.RangerViewModel

@Composable
fun AdminDashboardScreen(viewModel: RangerViewModel) {
    val context = LocalContext.current
    BackHandler { viewModel.logout() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("COMMANDER OPS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Button(onClick = { viewModel.purgeSystem() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF330000)), contentPadding = PaddingValues(horizontal=8.dp)) {
                Icon(Icons.Default.DeleteForever, null, tint = Color.Red); Text("PURGE", color = Color.Red)
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)), modifier = Modifier.fillMaxWidth().padding(bottom=16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Verification Status", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Total: ${viewModel.totalScans}", color = Color.Gray)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(4.dp)).background(Color.DarkGray)) {
                    if (viewModel.totalScans > 0) {
                        val successWeight = viewModel.successfulScans.toFloat() / viewModel.totalScans.toFloat()
                        val failWeight = viewModel.failedScans.toFloat() / viewModel.totalScans.toFloat()
                        if (successWeight > 0) Box(Modifier.weight(successWeight).fillMaxHeight().background(Color(0xFF00C853)))
                        if (failWeight > 0) Box(Modifier.weight(failWeight).fillMaxHeight().background(Color(0xFFD50000)))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("✔ ${viewModel.successfulScans} Granted", color = Color(0xFF00C853), fontSize = 12.sp)
                    Text("✖ ${viewModel.failedScans} Denied", color = Color(0xFFD50000), fontSize = 12.sp)
                }
            }
        }
        Text("FIELD LOGS (OFFLINE)", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(viewModel.globalGeoLogs.size) { i ->
                val log = viewModel.globalGeoLogs[i]
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