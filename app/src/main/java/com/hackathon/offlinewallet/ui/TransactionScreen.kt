package com.hackathon.offlinewallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.hackathon.offlinewallet.data.WalletTransactions
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    walletViewModel: WalletViewModel = hiltViewModel()
) {
    val email by remember { derivedStateOf { authViewModel.getCurrentUserEmail() } }
    val userId by remember { derivedStateOf { authViewModel.getCurrentUserId() } }
    val transactionsState by walletViewModel.getTransactions(userId ?: "").collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle case where userId is null (user not logged in)
    if (userId == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Please log in to view transactions",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction History") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (transactionsState.isEmpty()) {
                Text(
                    text = "No transactions found",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn {
                    items(transactionsState, key = { it.id }) { transaction ->
                        TransactionItem(transaction)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItem(walletTransactions: WalletTransactions) {
    // Determine color and icon based on transaction type
    val isSend = walletTransactions.type == "send"
    val transactionColor = if (isSend) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val icon = if (isSend) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward

    // Format timestamp to date only (e.g., "25 Jul 2025")
    val formattedDate = try {
        val dateTime = OffsetDateTime.parse(walletTransactions.timestamp)
        dateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    } catch (e: DateTimeParseException) {
        walletTransactions.timestamp // Fallback to raw timestamp if parsing fails
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon with colored background
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = transactionColor.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = if (isSend) "Sent" else "Received",
                        modifier = Modifier.size(24.dp),
                        tint = transactionColor
                    )
                }
                // Transaction details
                Column {
                    Text(
                        text = when {
                            isSend -> "Sent to ${walletTransactions.receiverEmail}"
                            else -> "Received from ${walletTransactions.senderEmail}"
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = walletTransactions.status.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = if (walletTransactions.status == "pending") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
            // Amount
            Text(
                text = "${if (isSend) "-" else "+"} ₹${String.format("%.2f", walletTransactions.amount)}",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = transactionColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}