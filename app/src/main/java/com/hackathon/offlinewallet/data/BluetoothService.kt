package com.hackathon.offlinewallet.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.UUID

class BluetoothService(
    private val context: Context,
    private val walletRepository: WalletRepository
) {
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
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
                    val buffer = ByteArray(1024)
                    val bytesRead = inputStream.read(buffer)
                    val transactionJson = String(buffer, 0, bytesRead)
                    val transaction = Json.decodeFromString(WalletTransactions.serializer(), transactionJson)
                    val receiverTransaction = transaction.copy(
                        id = UUID.randomUUID().toString(),
                        userId = "offline_${transaction.receiverEmail.hashCode()}",
                        type = "receive"
                    )

                    runBlocking {
                        withContext(Dispatchers.IO) {
                            walletRepository.storeOfflineTransaction(receiverTransaction)
                        }
                    }

                    inputStream.close()
                    socket.close()
                } catch (e: Exception) {
                    Log.e("BluetoothService", "Error receiving transaction", e)
                }
            }
        }.start()
    }
}