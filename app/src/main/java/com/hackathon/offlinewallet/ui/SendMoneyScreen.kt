package com.hackathon.offlinewallet.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
fun SendMoneyScreen(navController: NavController, viewModel: WalletViewModel) {
    var amount by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: QR, 1: UPI
    val wallet by viewModel.wallet.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scanner = GmsBarcodeScanning.getClient(context)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scanQrCode(scanner, onSuccess = { recipientId ->
                recipient = recipientId
            }, onError = {
                LaunchedEffect(Unit) {
                    snackbarHostState.showSnackbar("Failed to scan QR code")
                }
            })
        } else {
            LaunchedEffect(Unit) {
                snackbarHostState.showSnackbar("Camera permission denied")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        if (wallet == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Send Money", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                // Tabs for QR and UPI
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Scan QR") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("UPI ID") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Amount input
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // QR or UPI input
                if (selectedTab == 0) {
                    Button(onClick = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }) {
                        Text("Scan QR Code")
                    }
                    if (recipient.isNotBlank()) {
                        Text(text = "Recipient: $recipient", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    OutlinedTextField(
                        value = recipient,
                        onValueChange = { recipient = it },
                        label = { Text("Enter UPI ID (e.g., user@upi)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    val amountDouble = amount.toDoubleOrNull()
                    when {
                        amountDouble == null || amountDouble <= 0 -> {
                            LaunchedEffect(Unit) {
                                snackbarHostState.showSnackbar("Invalid amount")
                            }
                        }
                        wallet.balance < amountDouble -> {
                            LaunchedEffect(Unit) {
                                snackbarHostState.showSnackbar("Insufficient balance")
                            }
                        }
                        recipient.isBlank() -> {
                            LaunchedEffect(Unit) {
                                snackbarHostState.showSnackbar("Recipient ID or UPI ID required")
                            }
                        }
                        else -> {
                            LaunchedEffect(Unit) {
                                val isUpi = selectedTab == 1
                                val success = viewModel.sendMoney(amountDouble, recipient, isUpi)
                                if (success) {
                                    snackbarHostState.showSnackbar("Transaction successful")
                                    navController.popBackStack()
                                } else {
                                    snackbarHostState.showSnackbar(
                                        if (isUpi) "UPI transaction failed: No internet" else "Transaction failed"
                                    )
                                }
                            }
                        }
                    }
                }) {
                    Text("Send")
                }
            }
        }
    }
}

private fun scanQrCode(scanner: GmsBarcodeScanner, onSuccess: (String) -> Unit, onError: () -> Unit) {
    scanner.startScan()
        .addOnSuccessListener { barcode ->
            val recipientId = barcode.rawValue
            if (recipientId != null && recipientId.isNotBlank()) {
                onSuccess(recipientId)
            } else {
                onError()
            }
        }
        .addOnFailureListener {
            onError()
        }
}