package com.hackathon.offlinewallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.hackathon.offlinewallet.data.Transaction

@Composable
fun TransactionScreen(navController: NavController, viewModel: WalletViewModel) {
    val transactions = viewModel.transactions.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Transaction History", style = MaterialTheme.typography.headlineSmall)
        LazyColumn {
            items(transactions.value) { transaction ->
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "${transaction.type}: $${transaction.amount}")
                        Text(text = "To: ${transaction.recipient}")
                        Text(text = "Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(transaction.timestamp)}")
                        Text(text = "Synced: ${if (transaction.isSynced) "Yes" else "No"}")
                    }
                }
            }
        }
    }
}