package com.hackathon.offlinewallet.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.hackathon.offlinewallet.ui.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothService(
    private val context: Context,
    private val walletRepository: WalletRepository,
    private val authRepository: AuthRepository
) {
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun ensureDiscoverable(context: Context) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (bluetoothAdapter?.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
            context.startActivity(discoverableIntent)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE])
    fun startListening() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val serverSocket: BluetoothServerSocket = bluetoothAdapter
            .listenUsingRfcommWithServiceRecord("OfflineWallet", uuid)

        Thread {
            while (true) {
                try {

                    val socket: BluetoothSocket = serverSocket.accept()
                    val inputStream: InputStream = socket.inputStream
                    val outputStream: OutputStream = socket.outputStream

                    // Receive handshake
                    val buffer = ByteArray(1024)
                    var bytesRead = inputStream.read(buffer)
                    val message = String(buffer, 0, bytesRead)
                    if (message == "HANDSHAKE") {
                        outputStream.write("ACK".toByteArray())
                        outputStream.flush()

                        // Receive transaction
                        bytesRead = inputStream.read(buffer)
                        val transactionJson = String(buffer, 0, bytesRead)
                        val transaction = Json.decodeFromString(WalletTransactions.serializer(), transactionJson)

                        runBlocking {
                            val userId = authRepository.getUserIdByEmail(transaction.receiverEmail)


                            val receiverTransaction = transaction.copy(
                                userId = userId.toString(),
                                type = "receive"
                            )

                            withContext(Dispatchers.IO) {
                                walletRepository.storeOfflineTransaction(receiverTransaction)
                            }
                        }

                        // Send ACK for transaction
                        outputStream.write("ACK_TRANSACTION".toByteArray())
                        outputStream.flush()
                    }

                    inputStream.close()
                    outputStream.close()
                    socket.close()
                } catch (e: Exception) {
                    Log.e("BluetoothService", "Error in Bluetooth communication", e)
                }
            }
        }.start()
    }
}
