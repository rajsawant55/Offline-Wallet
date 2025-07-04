package com.hackathon.offlinewallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.hackathon.offlinewallet.data.WalletTransactions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    walletViewModel: WalletViewModel = hiltViewModel()
) {
    val userId = authViewModel.getCurrentUserEmail()?.let { "offline_${it.hashCode()}" } ?: return
    val transactions = walletViewModel.getTransactions(userId).collectAsState().value
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Transaction History") }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (transactions.isEmpty()) {
                Text(
                    text = "No transactions found",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn {
                    items(transactions) { transaction ->
                        TransactionItem(transaction)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItem(walletTransactions: WalletTransactions) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = when (walletTransactions.type) {
                        "send" -> "Sent to ${walletTransactions.receiverEmail}"
                        "receive" -> "Received from ${walletTransactions.senderEmail}"
                        else -> walletTransactions.type
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = walletTransactions.timestamp,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = walletTransactions.status.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (walletTransactions.status == "pending") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "${if (walletTransactions.type == "send") "-" else "+"} ₹${walletTransactions.amount}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (walletTransactions.type == "send") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}