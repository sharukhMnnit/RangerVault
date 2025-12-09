package com.example.rangervault.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rangervault.viewmodel.RangerViewModel

@Composable
fun LoginScreen(viewModel: RangerViewModel) {
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
            onClick = { if (name.isNotEmpty()) viewModel.login(name, role) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("ACCESS SYSTEM", color = Color.Black, fontWeight = FontWeight.Bold) }
    }
}