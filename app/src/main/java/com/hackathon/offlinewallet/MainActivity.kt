package com.hackathon.offlinewallet

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.Configuration
import com.hackathon.offlinewallet.data.BluetoothService
import com.hackathon.offlinewallet.ui.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.work.WorkManager
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var bluetoothService: BluetoothService

    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            checkAndEnableBluetooth()
        } else {
            Toast.makeText(this, "Bluetooth permissions denied", Toast.LENGTH_LONG).show()
        }
    }

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
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestBluetoothPermissions.launch(permissions)
        } else {
            checkAndEnableBluetooth()
        }
    }

    private fun checkAndEnableBluetooth() {
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
    }

    private fun tryStartBluetoothService() {
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        if (requiredPermissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            Log.e("MainActivity", "Missing required Bluetooth permissions")
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
            return
        }

        try {
            bluetoothService.startListening()
        } catch (e: SecurityException) {
            Log.e("MainActivity", "SecurityException in startListening: ${e.message}", e)
            Toast.makeText(this, "Bluetooth permission error", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start Bluetooth service: ${e.message}", e)
            Toast.makeText(this, "Failed to start Bluetooth service", Toast.LENGTH_LONG).show()
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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "login",
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