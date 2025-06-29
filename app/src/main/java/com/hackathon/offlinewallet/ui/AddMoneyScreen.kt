package com.hackathon.offlinewallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun AddMoneyScreen(navController: NavController, viewModel: WalletViewModel) {
    var amount by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Add Money", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            amount.toDoubleOrNull()?.let {
                viewModel.addMoney(it)
                navController.popBackStack()
            }
        }) {
            Text("Add")
        }
    }
}