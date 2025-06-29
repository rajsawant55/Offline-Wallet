package com.hackathon.offlinewallet.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.mlkit.vision.codescanner.*
import kotlinx.coroutines.launch

@Composable
fun SendMoneyScreen(navController: NavController, viewModel: WalletViewModel) {
    var amount by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: QR, 1: UPI
    val wallet by viewModel.wallet.collectAsState()
    val context = LocalContext.current
    val scanner = GmsBarcodeScanning.getClient(context)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope() // Coroutine scope for launching snackbar
    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scanQrCode(scanner, onSuccess = { recipientId ->
                recipient = recipientId
            }, onError = {
                scope.launch { snackbarHostState.showSnackbar("Failed to scan QR code") }
            })
        } else {
            scope.launch { snackbarHostState.showSnackbar("Camera permission denied") }
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
                        text = { Text("Scan QR") },
                        enabled = hasCamera
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
                    if (hasCamera) {
                        Button(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                scanQrCode(scanner, onSuccess = { recipientId ->
                                    recipient = recipientId
                                }, onError = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Failed to scan QR code")
                                    }
                                })
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }) {
                            Text("Scan QR Code")
                        }
                    } else {
                        Text(
                            text = "Camera not available on this device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
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
                            scope.launch {
                                snackbarHostState.showSnackbar("Invalid amount")
                            }
                        }
                        wallet!!.balance < amountDouble -> {
                            scope.launch {
                                snackbarHostState.showSnackbar("Insufficient balance")
                            }
                        }
                        recipient.isBlank() -> {
                            scope.launch {
                                snackbarHostState.showSnackbar("Recipient ID or UPI ID required")
                            }
                        }
                        else -> {
                            val isUpi = selectedTab == 1
                            scope.launch {
                                val success = viewModel.sendMoney(
                                    amountDouble, recipient, "UPI",
                                    isUpi
                                )
                                snackbarHostState.showSnackbar(
                                    if (success) "Transaction successful" else {
                                        if (isUpi) "UPI transaction failed: No internet"
                                        else "Transaction failed"
                                    }
                                )
                                if (success) navController.popBackStack() // Navigate back on success
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