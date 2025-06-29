package com.hackathon.offlinewallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun MerchantPaymentScreen(navController: NavController, viewModel: WalletViewModel) {
    var amount by remember { mutableStateOf("") }
    var merchantId by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Pay Merchant", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = merchantId,
            onValueChange = { merchantId = it },
            label = { Text("Merchant ID") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            amount.toDoubleOrNull()?.let {
                viewModel.sendMoney(it, merchantId, "MERCHANT", false)
                navController.popBackStack()
            }
        }) {
            Text("Pay")
        }
    }
}