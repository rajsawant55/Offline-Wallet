package com.hackathon.offlinewallet

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hackathon.offlinewallet.data.BluetoothService
import com.hackathon.offlinewallet.ui.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var bluetoothService: BluetoothService

    @SuppressLint("MissingPermission")
    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            checkAndEnableBluetooth()
        } else {
            Toast.makeText(this, "Bluetooth permissions denied", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private val enableBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            tryStartBluetoothService()
        } else {
            Toast.makeText(this, "Bluetooth is required for offline transactions", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAndRequestBluetoothPermissions()
        setContent {
            WalletApp()
        }
    }

    private fun checkAndRequestBluetoothPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestBluetoothPermissions.launch(permissions)
        } else {
            checkAndEnableBluetooth()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH,Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    private fun checkAndEnableBluetooth() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Log.e("MainActivity", "Device does not support Bluetooth")
                Toast.makeText(this, "Device does not support Bluetooth", Toast.LENGTH_LONG).show()
                return
            }
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetooth.launch(enableBtIntent)
            } else {
                tryStartBluetoothService()
            }
        } catch (e: SecurityException) {
            Log.e("MainActivity", "SecurityException in checkAndEnableBluetooth: ${e.message}", e)
            Toast.makeText(this, "Bluetooth permission error", Toast.LENGTH_LONG).show()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE])
    private fun tryStartBluetoothService() {
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
        if (requiredPermissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            Log.e("MainActivity", "Missing required Bluetooth permissions")
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
            return
        }

        try {
            ensureDiscoverable()
            bluetoothService.startListening()
        } catch (e: SecurityException) {
            Log.e("MainActivity", "SecurityException in startListening: ${e.message}", e)
            Toast.makeText(this, "Bluetooth permission error", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start Bluetooth service: ${e.message}", e)
            Toast.makeText(this, "Failed to start Bluetooth service", Toast.LENGTH_LONG).show()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun ensureDiscoverable() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter?.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
                startActivity(discoverableIntent)
            }
        } catch (e: SecurityException) {
            Log.e("MainActivity", "SecurityException in ensureDiscoverable: ${e.message}", e)
            Toast.makeText(this, "Bluetooth discoverability permission error", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun WalletApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val walletViewModel: WalletViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val user by authViewModel.user.collectAsState()
    val startDestination = if (user != null) "home" else "login"

    LaunchedEffect(user) {
        if (user != null && navController.currentDestination?.route != "home") {
            navController.navigate("home") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("login") {
                LoginScreen(navController, authViewModel)
            }
            composable("register") {
                RegistrationScreen(navController, authViewModel)
            }
            composable("home") {
                HomeScreen(navController, authViewModel, walletViewModel)
            }
            composable("add_money") {
                AddMoneyScreen(navController, authViewModel, walletViewModel)
            }
            composable("send_money") {
                SendMoneyScreen(navController, authViewModel, walletViewModel)
            }
            composable("receive_money") {
                ReceiveMoneyScreen(navController, authViewModel, walletViewModel)
            }
            composable("merchant_payment") {
                MerchantPaymentScreen(navController, authViewModel, walletViewModel)
            }
            composable("transactions") {
                TransactionScreen(navController, authViewModel, walletViewModel)
            }
        }
    }
}