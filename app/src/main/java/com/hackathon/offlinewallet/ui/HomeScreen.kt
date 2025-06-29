package com.hackathon.offlinewallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue // Import getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle // Import collectAsStateWithLifecycle
import androidx.navigation.NavController

@Composable
fun HomeScreen(navController: NavController, viewModel: WalletViewModel) {
    // Collect StateFlows as State
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Offline First Wallet", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Status: ${if (isOnline) "Online" else "Offline"}") // isOnline is now a direct Boolean
        Text(text = "Balance: $${wallet?.balance ?: 0.0}") // wallet is now directly the WalletData object
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("add_money") }) { Text("Add Money") }
        Button(onClick = { navController.navigate("send_money") }) { Text("Send Money") }
		Button(onClick = { navController.navigate("receive_money") }) { Text("Receive Money") }																				   
        Button(onClick = { navController.navigate("merchant_payment") }) { Text("Pay Merchant") }
        Button(onClick = { navController.navigate("transactions") }) { Text("View Transactions") }
    }
}