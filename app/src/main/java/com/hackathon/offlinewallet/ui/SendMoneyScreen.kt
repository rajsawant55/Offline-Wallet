package com.hackathon.offlinewallet.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.hackathon.offlinewallet.data.WalletTransactions
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.OffsetDateTime
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendMoneyScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    walletViewModel: WalletViewModel = hiltViewModel()
) {
    var receiverEmail by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val isOnline by walletViewModel.isOnline.collectAsState()
    var isBluetoothEnabled by remember { mutableStateOf(BluetoothAdapter.getDefaultAdapter()?.isEnabled == true) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showPairingPrompt by remember { mutableStateOf(false) }
    val discoveredDevices = remember { mutableStateListOf<BluetoothDevice>() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.all { it.value }) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Camera or Bluetooth permissions denied")
            }
        } else {
            isBluetoothEnabled = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            receiverEmail = result.contents
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isBluetoothEnabled = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        if (BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.isNotEmpty() == true) {
            showDeviceDialog = true
        }
    }

    // Register BroadcastReceiver for Bluetooth discovery
    DisposableEffect(Unit) {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (permissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(permissions)
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null && !discoveredDevices.contains(device)) {
                            discoveredDevices.add(device)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        // Discovery finished, dialog will show discovered devices
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        }
    }

    // Device selection dialog
    if (showDeviceDialog) {
        DeviceSelectionDialog(
            pairedDevices = BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList() ?: emptyList(),
            discoveredDevices = discoveredDevices,
            onDeviceSelected = { device ->
                selectedDevice = device
                showDeviceDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Selected device: ${device.name ?: "Unknown"}")
                }
            },
            onDismiss = {
                showDeviceDialog = false
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            }
        )
    }

    // Pairing prompt dialog
    if (showPairingPrompt) {
        AlertDialog(
            onDismissRequest = { showPairingPrompt = false },
            title = { Text("No Paired Devices") },
            text = { Text("No paired Bluetooth devices found. Would you like to pair a device now?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPairingPrompt = false
                        settingsLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                ) {
                    Text("Pair Device")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPairingPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Money") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Send Money",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = receiverEmail,
                        onValueChange = { receiverEmail = it },
                        label = { Text("Receiver Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Scan recipient's QR code")
                                setCameraId(0)
                                setBeepEnabled(true)
                            }
                            scanLauncher.launch(options)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isBluetoothEnabled
                    ) {
                        Text("Scan QR Code")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            discoverAndSelectDevice(context) {
                                showDeviceDialog = true
                                showPairingPrompt = BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.isEmpty() ?: true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isBluetoothEnabled
                    ) {
                        Text("Select Bluetooth Device")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            isLoading = true
                            val amountDouble = amount.toDoubleOrNull()
                            val senderEmail = authViewModel.getCurrentUserEmail()
                            if (amountDouble == null || amountDouble <= 0 || receiverEmail.isBlank() || senderEmail == null) {
                                isLoading = false
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Please enter a valid email and amount")
                                }
                                return@Button
                            }
                            if (receiverEmail == senderEmail) {
                                isLoading = false
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Cannot send money to self")
                                }
                                return@Button
                            }
                            if (isOnline) {
                                walletViewModel.sendMoney(senderEmail, receiverEmail, amountDouble) { error ->
                                    isLoading = false
                                    if (error == null) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("₹$amount sent to $receiverEmail successfully")
                                            navController.popBackStack()
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(error)
                                        }
                                    }
                                }
                            } else {
                                if (selectedDevice == null) {
                                    isLoading = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Please select a Bluetooth device")
                                    }
                                    return@Button
                                }
                                coroutineScope.launch(Dispatchers.IO) {
                                    val wallet = walletViewModel.getWallet(senderEmail)
                                    val senderBalance: Double? = wallet.value?.balance
                                    withContext(Dispatchers.Main) {
                                        if (senderBalance != null) {
                                            if (senderBalance < amountDouble) {
                                                snackbarHostState.showSnackbar("Insufficient balance")
                                                navController.popBackStack()
                                            }
                                        }
                                    }
                                }
                                coroutineScope.launch(Dispatchers.IO) {
                                    val transaction = WalletTransactions(
                                        id = UUID.randomUUID().toString(),
                                        userId = "offline_${senderEmail.hashCode()}",
                                        senderEmail = senderEmail,
                                        receiverEmail = receiverEmail,
                                        amount = amountDouble,
                                        type = "send",
                                        timestamp = OffsetDateTime.now().toString(),
                                        status = "pending"
                                    )
                                    walletViewModel.storeOfflineTransaction(transaction)
                                    val success = sendViaBluetooth(context, transaction, selectedDevice!!)
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        if (success) {
                                            snackbarHostState.showSnackbar("Transaction sent via Bluetooth, will sync when online")
                                            navController.popBackStack()
                                        } else {
                                            snackbarHostState.showSnackbar("Bluetooth transfer failed, transaction saved locally")
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(scale)
                            .height(48.dp),
                        interactionSource = interactionSource,
                        enabled = !isLoading && isBluetoothEnabled
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(if (isOnline) "Send Money Online" else "Send Money via Bluetooth")
                        }
                    }
                }
            }
        }
    }
}

@RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
fun discoverAndSelectDevice(context: Context, onDiscoveryStarted: () -> Unit) {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
        Log.w("SendMoneyScreen", "Bluetooth is not enabled or not supported")
        return
    }

    val pairedDevices = bluetoothAdapter.bondedDevices
    // Trigger dialog, let the dialog handle pairing prompt if no paired devices
    bluetoothAdapter.startDiscovery()
    onDiscoveryStarted()
}

@Composable
fun DeviceSelectionDialog(
    pairedDevices: List<BluetoothDevice>,
    discoveredDevices: List<BluetoothDevice>,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Select Bluetooth Device") },
        text = {
            Column {
                if (pairedDevices.isEmpty() && discoveredDevices.isEmpty()) {
                    Text("No devices found. Please ensure Bluetooth is enabled and devices are discoverable.")
                } else {
                    LazyColumn {
                        if (pairedDevices.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Paired Devices",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(pairedDevices, key = { it.address }) { device ->
                                DeviceItem(device, onDeviceSelected)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                        if (discoveredDevices.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Discovered Devices",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(discoveredDevices, key = { it.address }) { device ->
                                DeviceItem(device, onDeviceSelected)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Cancel")
            }
        }
    )
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice, onDeviceSelected: (BluetoothDevice) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeviceSelected(device) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = device.name ?: "Unknown Device (${device.address})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
fun sendViaBluetooth(context: Context, transaction: WalletTransactions, device: BluetoothDevice): Boolean {
    return try {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return false

        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
        bluetoothAdapter.cancelDiscovery()
        socket.connect()

        val outputStream: OutputStream = socket.outputStream
        val inputStream: InputStream = socket.inputStream

        // Send handshake
        outputStream.write("HANDSHAKE".toByteArray())
        outputStream.flush()

        // Wait for acknowledgment
        val buffer = ByteArray(1024)
        val bytesRead = inputStream.read(buffer)
        val response = String(buffer, 0, bytesRead)
        if (response != "ACK") {
            socket.close()
            return false
        }

        // Send transaction
        val transactionJson = Json.encodeToString(WalletTransactions.serializer(), transaction)
        outputStream.write(transactionJson.toByteArray())
        outputStream.flush()

        outputStream.close()
        inputStream.close()
        socket.close()
        true
    } catch (e: Exception) {
        Log.e("SendMoneyScreen", "Bluetooth transfer failed", e)
        false
    }
}